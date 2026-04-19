package io.github.bareboneslib.bareboneshll;

import java.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import net.openhft.hashing.LongHashFunction;


/**
 * Thin CLI shim for interop testing.
 *
 * Reads one JSON command from stdin, writes one JSON result to stdout.
 *
 * Protocol:
 *   serialize:   {"op":"serialize", "p":int, "r":int, "values":[long,...]}
 *                → {"bytes":"{@code <b64>}", "estimate":long}
 *
 *   deserialize: {"op":"deserialize", "bytes":"{@code <b64>}"}
 *                → {"estimate":long}
 *
 *   merge:       {"op":"merge", "sketches":["{@code <b64>}","{@code <b64>}",...]}
 *                → {"bytes":"{@code <b64>}", "estimate":long}
 */
public class HLLCli {
    static LongHashFunction hash = LongHashFunction.xx();

    public static void main(String[] args) throws Exception {
        String input = new BufferedReader(new InputStreamReader(System.in)).readLine();
        JSONObject cmd = new JSONObject(input);
        String op = cmd.getString("op");

        JSONObject result = null;
        try {
            switch(op) {
                case "serialize":
                    result = serialize(cmd);
                    break;
                case "deserialize":
                    result = deserialize(cmd);
                    break;
                case "merge":
                    result = merge(cmd);
                    break;
                default: throw new IllegalArgumentException("Unknown op: " + op);
            }
        }
        catch(Exception e) {
            e.printStackTrace();
        }

        System.out.println(result.toString());
    }

    private static JSONObject serialize(JSONObject cmd) throws Exception {
        int p = cmd.getInt("p");
        int r = cmd.getInt("r");
        JSONArray vals = cmd.getJSONArray("values");

        HLLPlusPlus hll = new HLLPlusPlus(p, r);
        for (int i = 0; i < vals.length(); i++) {
            hll.add(hash.hashLong(vals.getLong(i)));
        }

        byte[] bytes = hll.serialize();
        return new JSONObject()
            .put("bytes",    Base64.getEncoder().encodeToString(bytes))
            .put("estimate", hll.estimate());
    }

    private static JSONObject deserialize(JSONObject cmd) throws Exception {
        byte[] bytes = Base64.getDecoder().decode(cmd.getString("bytes"));
        HLLPlusPlus hll = HLLPlusPlus.deserialize(bytes);
        return new JSONObject()
            .put("estimate", hll.estimate());
    }

    private static JSONObject merge(JSONObject cmd) throws Exception {
        JSONArray sketches = cmd.getJSONArray("sketches");

        // deserialize first sketch as the base
        byte[] firstBytes = Base64.getDecoder().decode(sketches.getString(0));
        HLLPlusPlus base  = HLLPlusPlus.deserialize(firstBytes);

        for (int i = 1; i < sketches.length(); i++) {
            byte[] b    = Base64.getDecoder().decode(sketches.getString(i));
            HLLPlusPlus other = HLLPlusPlus.deserialize(b);
            if (!base.merge(other)) {
                throw new IllegalArgumentException(
                    "Incompatible sketches at index " + i + " (p/r mismatch)");
            }
        }

        byte[] merged = base.serialize();
        return new JSONObject()
            .put("bytes",    Base64.getEncoder().encodeToString(merged))
            .put("estimate", base.estimate());
    }
}
