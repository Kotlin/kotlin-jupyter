# standard JVM options environment variable
JAVA_OPTS = "JAVA_OPTS"

# specific JVM options environment variable
KERNEL_JAVA_OPTS = "KOTLIN_JUPYTER_JAVA_OPTS"

# additional JVM options to add to either JAVA_OPTS or KOTLIN_JUPYTER_JAVA_OPTS
KERNEL_EXTRA_JAVA_OPTS = "KOTLIN_JUPYTER_JAVA_OPTS_EXTRA"

# used internally to add JVM options without overwriting KOTLIN_JUPYTER_JAVA_OPTS_EXTRA
KERNEL_INTERNAL_ADDED_JAVA_OPTS = "KOTLIN_JUPYTER_KERNEL_EXTRA_JVM_OPTS"

# standard JDK location environment variable
JAVA_HOME = "JAVA_HOME"

# specific JDK location environment variable
KERNEL_JAVA_HOME = "KOTLIN_JUPYTER_JAVA_HOME"

# java executable path. If set, has greater priority than KOTLIN_JUPYTER_JAVA_HOME
KERNEL_JAVA_EXECUTABLE = "KOTLIN_JUPYTER_JAVA_EXECUTABLE"
