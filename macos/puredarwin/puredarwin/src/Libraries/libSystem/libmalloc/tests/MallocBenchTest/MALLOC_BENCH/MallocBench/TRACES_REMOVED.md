# MallocBench trace fixtures removed

The large `*.ops` allocation-trace fixtures that normally live in this directory
(facebook, flickr, nimlang, reddit, theverge, and their `_memory_warning`
variants — ~150 MB total) were **intentionally removed** from this warehouse
snapshot. They are benchmark *input data* for the libmalloc MallocBench test
harness and are **not required to build libmalloc or the OS**.

If you need to run the MallocBench benchmarks, restore the traces from the
upstream PureDarwin / libmalloc source:

- `github.com/PureDarwin/PureDarwin` (the `libmalloc` subtree under
  `src/Libraries/libSystem/libmalloc`), or
- `github.com/apple-oss-distributions/libmalloc`.

The benchmark **source code** (`*.cpp` / `*.h`) is retained in this directory;
only the oversized trace data files were dropped to keep the repository lean.
