"""
Driver registry — the only file you touch when adding a new language.

To add a new language:
  1. Write interop_tests/drivers/rust_driver.py  (same 3 functions: serialize / deserialize / merge)
  2. Add one line here:  from drivers import rust_driver
  3. Add it to DRIVERS

That's it — all n² interop test combinations are generated automatically.
"""

from drivers import cpp_driver, java_driver

# Each entry: (name shown in pytest output, driver module)
DRIVERS = [
    ("java", java_driver),
    ("cpp",  cpp_driver),
]
