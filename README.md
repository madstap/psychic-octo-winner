# Kitchen, an orders simulation

## Requirements

* Clojure CLI
* Babashka

## Running the tests


```
$ bb test
```

## Running from the command line

Running with the defaults

```
$ clojure -X kitchen.core/main
```

Changing the ingest rate

```
$ clojure -X kitchen.core/main :ingest-rate 20
```

## Running from the REPL

See the comment at the end of `kitchen.core.clj`

## Structure

The main namespaces are:

`kitchen.kitchen` implements the main flow.

`kitchen.ingest-orders` and `kitchen.courier` contain
the other async processes for ingesting orders and dispatching couriers.

The `kitchen.shelves` namespace implements the state transitions.
