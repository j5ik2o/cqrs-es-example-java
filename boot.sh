#!/bin/sh

for f in $(ls /app/lib/*.jar); do
  CLASSPATH="${CLASSPATH}:$f"
done

java -cp "${CLASSPATH}" com.github.j5ik2o.cqrs.es.java.main.Main $@