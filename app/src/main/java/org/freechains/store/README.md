# Store - Freechains

Interprets a chain as a dataset -- a map of maps -- from a list of posted
triples.

A store is associated with an existing chain and port to connect.
It provides the dataset `data` a mutable list of callbacks `cbs` that are
called when new data is posted, and a function `store` to update the store.

```
class Store (chain: String, port: Int) {
    val data : MutableMap<String,MutableMap<String,String>>
    val cbs  : MutableList<(String,String,String)->Unit>
    fun store (v1: String, v2: String, v3: String)
}
```

- Instantiate a store from an existing chain:

```
val s = Store("#data", 8330)    // port to connect
```

- Post a triple in the store:

```
s.store("v1","v2","v3")
```

- Read the store:

```
assert(s.data["v1"]!!["v2"]!! == "v3")
```

- Remove an item with the special symbol `"REM"`:

```
s.store("v1","v2","REM")
assert(!s.data["v1"]!!.containsKey("v2"))
```
