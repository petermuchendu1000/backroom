#!/usr/bin/env python3
"""Fail unless a JUnit XML report proves a test suite actually executed.

Gradle's Test task exits 0 when it discovers no tests, so "BUILD SUCCESSFUL"
does not by itself prove anything ran. Gradle also prints no test count at the
default log level, so a suite that silently stopped being discovered -- a
deleted class, a renamed source set, a broken filter -- reads as a pass.

This asserts on the report Gradle writes and prints the per-suite counts, so
both the machine and a human reading the log can see a drop.

Usage: assert_junit_results.py <results-dir> <minimum-tests>
Exit:  0 = the suite ran and every test passed
       1 = assertion failed
       2 = bad invocation
"""

import glob
import os
import sys
import xml.etree.ElementTree as ElementTree


def collect(results_dir):
    """Return (suites, report_count). suites is a list of count dicts."""
    paths = sorted(glob.glob(os.path.join(results_dir, "TEST-*.xml")))
    suites = []
    for path in paths:
        root = ElementTree.parse(path).getroot()
        # A report file is either a single <testsuite> or a <testsuites> wrapper.
        elements = [root] if root.tag == "testsuite" else list(root.iter("testsuite"))
        for element in elements:
            suites.append(
                {
                    "name": element.get("name", os.path.basename(path)),
                    "tests": int(element.get("tests", 0)),
                    "failures": int(element.get("failures", 0)),
                    "errors": int(element.get("errors", 0)),
                    "skipped": int(element.get("skipped", 0)),
                }
            )
    return suites, len(paths)


def main(argv):
    if len(argv) != 3:
        print(__doc__, file=sys.stderr)
        return 2
    results_dir, minimum = argv[1], int(argv[2])

    suites, report_count = collect(results_dir)

    if not suites:
        print(
            "FAIL: no JUnit report containing a <testsuite> was found under "
            f"{results_dir} -- the suite did not run",
            file=sys.stderr,
        )
        return 1

    for suite in suites:
        print(
            "  {name}: tests={tests} failures={failures} errors={errors} "
            "skipped={skipped}".format(**suite)
        )

    total = {key: sum(s[key] for s in suites) for key in ("tests", "failures", "errors", "skipped")}
    passed = total["tests"] - total["failures"] - total["errors"] - total["skipped"]
    print(
        "TOTAL: {tests} tests / {passed} passed / {failures} failed / {errors} errored / "
        "{skipped} skipped  (across {reports} report file(s), {suites} suite(s))".format(
            passed=passed, reports=report_count, suites=len(suites), **total
        )
    )

    failed = False
    if total["tests"] < minimum:
        print(
            f"FAIL: {total['tests']} test(s) ran, expected at least {minimum}. "
            "Either tests stopped being discovered, or the floor in the workflow "
            "needs updating in the same commit that changed the suite.",
            file=sys.stderr,
        )
        failed = True
    if total["failures"] or total["errors"]:
        print(
            f"FAIL: {total['failures']} failure(s) and {total['errors']} error(s)",
            file=sys.stderr,
        )
        failed = True
    if total["skipped"]:
        print(
            f"FAIL: {total['skipped']} test(s) skipped -- a silenced test is not a "
            "passing test. Remove the @Ignore or lower the floor deliberately.",
            file=sys.stderr,
        )
        failed = True

    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
