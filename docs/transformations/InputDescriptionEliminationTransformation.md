# Input Description Elimination Transformation

## Overview

This transformation removes every argument of the `input` function.

## Example

Before:
```python
name = input('What is your name')
```

After:
```python
name = input()
```

---

Before:
```python
string = input(__prompt='Hello world')
```

After:
```python
string = input()
```

## Algorithm

`input` function call is detected by traversing the AST and comparing the name of the callee.
Then all the arguments are removed from the AST.
