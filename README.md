# Java-micro-http-server

Single file Http-server based on sources of the article: ["A Simple HTTP Server in Java"](https://commandlinefanatic.com/cgi-bin/showarticle.cgi?article=art076) by Joshua Davies

# Purpose
Proxying Json objects from different sources and changing its http-header, f.e: ```Access-Control-Allow-Origin``` to ```*```

Use JDK 11+ for run java-source without compiling:

```
.\JDK11.0.5\bin\java --source 8 HttpServer.java %1
```
