# Pushy Benchmarks

This module includes benchmarks for various Pushy components. They're useful mostly for development, and aren't intended for distribution as binaries. The benchmarks use [jmh](http://openjdk.java.net/projects/code-tools/jmh/) (Java microbenchmark harness).

To build the benchmarks:

```sh
mvn clean install
```

To run the benchmarks:

```sh
java -jar target/benchmarks.jar
```

A full discussion of best practices for writing and running benchmarks is beyond the scope of this document, but please see the [jmh samples](http://hg.openjdk.java.net/code-tools/jmh/file/tip/jmh-samples/src/main/java/org/openjdk/jmh/samples/) as a starting point for working with jmh in general.
