###### READ THIS ######
# Please note that this Dockerfile is NOT for actual deployment! This is
# ONLY to help out writing new content for the site without having to install
# Java and Leiningen. Combined with the compose file this is by far the easiest
# way to get up and running.
#
# Use the official Clojure image with Leiningen installed
FROM clojure:lein

# Set the working directory inside the container
WORKDIR /app

# Set locale to UTF-8 for the OS and Java
ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8
ENV LEIN_JVM_OPTS="-Dfile.encoding=UTF-8"

# Expose the port used by the web server
EXPOSE 3000

# Default command to run
CMD ["lein", "run-local"]