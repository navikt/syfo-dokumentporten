# Work with diagrams made with PlantUml

We use [PlantUml](https://plantuml.com) to define a diagram through code.
To work with plantUml and get quick previews, it is recommended to install a plugin for your editor of choice.
There are plugins for Intellij and VS Code available at least.

In order to generate an image for the diagrams specified in the .puml files, run the commands

## lps-request-diagram.png
```bash
 docker run --rm \
  -v "$(pwd)":/workspace \
  -w /workspace \
  plantuml/plantuml lps-request-diagram.puml
```

## database-diagram.png
```bash
 docker run --rm \
  -v "$(pwd)":/workspace \
  -w /workspace \
  plantuml/plantuml database-erd.puml
```
