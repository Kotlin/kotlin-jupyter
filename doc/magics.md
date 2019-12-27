# Line magics
The following line magics are supported:
 - `%use <lib1>, <lib2> ...` - injects code for supported libraries: artifact resolution, default imports, initialization code, type renderers
 - `%trackClasspath` - logs any changes of current classpath. Useful for debugging artifact resolution failures
 - `%trackExecution` - logs pieces of code that are going to be executed. Useful for debugging of libraries support
 - `%output [--max-cell-size=N] [--max-buffer=N] [--max-buffer-newline=N] [--max-time=N] [--no-stdout] [--reset-to-defaults]` - 
 output capturing settings.
     - `max-cell-size` specifies the characters count which may be printed to stdout. Default is 100000.
     - `max-buffer` - max characters count stored in internal buffer before being sent to client. Default is 10000.
     - `max-buffer-newline` - same as above, but trigger happens only if newline character was encountered. Default is 100.
     - `max-time` - max time in milliseconds before the buffer is sent to client. Default is 100.
     - `no-stdout` - don't capture output. Default is false.
     - `reset-to-defaults` - reset all output settings that were set with magics to defaults
 