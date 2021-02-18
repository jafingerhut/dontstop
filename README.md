# Interrupting long-running evaluations in a Clojure REPL

There are many ways to start a Clojure REPL.  They have many differing
properties, including whether they enable you to interrupt a
long-running expression that you have initiated by attempting to
evaluate an expression in the REPL.

This repository is intended to collect together at least some of the
ways of starting a Clojure REPL, and whether they let you interrupt a
long-running expression in the REPL, and if so, how.

It also has a concrete example of the potential dangers of
interrupting expression evaluation.  The purpose of this is not to
dogmatically assert that one should never do this, but to educate
developers about the potential consequences, so they can make
better-informed decisions.

Glossary:

+ IDE - Integrated Development Environment
+ JVM - Java Virtual Machine
+ REPL - Read, Eval, Print, Loop

This repository's name is a reference to the [Fleetwood Mac
song](https://en.wikipedia.org/wiki/Don%27t_Stop_(Fleetwood_Mac_song)).


## Why are there so many ways to start a Clojure REPL?

Different developers have varying preferences on such things as:

+ what editor / IDE they prefer to use
+ whether they start a REPL from that IDE or via some command executed
  outside of that IDE
+ what kinds of additional software they run on each "side" of a
  connection between the Clojure runtime process(es) and the IDE
  process(es) that enable features like:
  + jump to definition
  + lookup of function documentation and argument lists
  + pretty-printing of results, perhaps with different colors to
    highlight different parts of the printed values
  + custom handling or formatting of exception that are thrown

These variations can lead not only to different enhancement
capabilities in the IDE of the developer, but also to how they can
send expressions to the REPL for evaluation, and whether the
evaluation of a long-running form can be interrupted.


## How to stop a long-running evaluation for some ways of starting a REPL

| How REPL is started | Method of interrupting evaluation | Underlying implementation |
| ------------------- | --------------------------------- | ------------------------- |
| `lein repl` command in a terminal with nrepl 0.6.0 | Ctrl-C typed in the terminal | Uses `interrupt` method followed immediately by `stop` method.  Search for `stop` in source file src/clojure/nrepl/middleware/session.clj in tag 0.6.0 of https://github.com/nrepl/nrepl |
| `lein repl` command in a terminal with nrepl 0.7.0 or later | Ctrl-C typed in the terminal | Uses `interrupt` method followed about 5.1 sec later by `stop` method, if `interrupt` method did not cause the thread to stop.  Search for `stop` in source file src/clojure/nrepl/middleware/session.clj in tag 0.7.0 of https://github.com/nrepl/nrepl |
| `clojure` or `clj` CLI command in a terminal (empty deps.edn file) | none.  Ctrl-C in terminal where command was started kills the entire JVM process | none |
| `unravel` command in a terminal | Ctrl-C typed in terminal where `unravel` started | Uses `stop` method. Search for 'stop' in this [source file](https://github.com/Unrepl/unrepl/blob/fa946eef88b0516dab81c8a9b3d8f9fcff06f44b/src/unrepl/repl.clj) |
| bb (babashka) | none, because GraalVM does not support deprecated `stop` method (source: babashka developer Michiel Borkent) | none |

A very quick way to test whether a particular REPL supports Ctrl-C to
stop evaluation of the current form is to do this inside of the REPL:

```clojure
user=> (def tmp1 (dorun (range)))
```

That will start an infinite loop.  Type Ctrl-C, or whatever keystroke
the particular REPL might document as stopping the currently
evaluating form.

If you get a new REPL prompt back, the JVM should still be running,
but that one thread that was evaluating that form was stopped.

If you get back to a prompt for your terminal or command shell, then
either:

+ the JVM process itself was killed, along with any state or data that
  was in that process's memory.
+ or, if you are using a REPL that is a separate process that connects
  to the JVM process running your Clojure code over some kind of
  connection, e.g. a TCP socket, it might be that only the separate
  process was killed, but the JVM process running your Cloure code is
  still running, and perhaps even still running the infinite loop.

Commands to show versions of various software you might have
installed:

+ Ubuntu Linux: `lsb_release -a`
+ JDK: `java -version`
+ [Leiningen](https://leiningen.org/): `lein version`
+ [Clojure CLI tools](https://clojure.org/guides/getting_started)
  (note: the version of the Clojure CLI tools software is separate
  from the Clojure language version you are using): `clj -Sdescribe`
+ [Unravel](https://github.com/Unrepl/unravel) REPL: `unravel --version`

Version combinations tested for `lein repl` in a terminal:

| OS | JDK | Leiningen | other versions |
| -- | --- | --------- | -------------- |
| Ubuntu 18.04.5 | OpenJDK 11.0.10 | 2.9.3 | REPL-y 0.4.4, nREPL 0.6.0 |
| Ubuntu 18.04.5 | OpenJDK 11.0.10 | 2.9.5 | REPL-y 0.4.4, nREPL 0.8.3 |
| macOS 10.14.6 | AdoptOpenJDK 15.0.1 | 2.9.3 | REPL-y 0.4.4, nREPL 0.6.0 |
| macOS 10.14.6 | AdoptOpenJDK 1.8.0_275 | 2.9.5 | REPL-y 0.4.4, nREPL 0.8.3 |
| Window 10 cmd.exe window | AdtopOpenJDK 11.0.9 | 2.9.5 | REPL-y 0.4.4, nREPL 0.8.3 |

Version combinations tested for `clojure` and `clj` in a terminal,
with empty `deps.edn` file:

| OS | JDK | clj / clojure |
| -- | --- | ------------- |
| Ubuntu 18.04.5 | OpenJDK 11.0.10 | 1.10.1.754 |
| macOS 10.14.6 | AdoptOpenJDK 1.8.0_275 | 1.10.1.754 |
| macOS 10.14.6 | AdoptOpenJDK 15.0.1 | 1.10.1.754 |

Version combinations tested for `unravel` command in a terminal:

| OS | unravel |
| -- | ------- |
| Ubuntu 18.04.5 | 0.3.0-beta (Lumo 1.7.0) |
| macOS 10.14.6 | 0.3.0-beta.2 (Lumo 1.7.0) |



## What can go wrong if I call the JVM method `stop` on a running thread?

If you talk to experienced Clojure developers (or developers of any
other programming language that runs on a JVM), and you ask about how
to stop a thread, but continue to keep the JVM process running
afterwards, many of them will insistently warn you that calling the
[`stop` method from class
`java.lang.Thread`](https://docs.oracle.com/javase/8/docs/api/java/lang/Thread.html#stop--)
is unsafe, and to avoid it if possible.

The Java documentation for the `stop` method at the previous link has
a paragraph-long deprecation warning, plus a link to a multi-page
article with recommendations on what should be done instead when you
wish to stop a thread in a safe way.  The example code and REPL
session below is intended to demonstrate the reason why `stop` is
deprecated in way that is easy to understand.

So what is the big deal with the `stop` method?

Below is a series of commands to start a Leiningen REPL in a terminal.
Using Leiningen version 2.9.3 (a recent version as of 2021-Feb-16),
and I believe many earlier versions, you can type Ctrl-C in such a
REPL session, and it will cause the `stop` method to be called on the
thread that is evaluating the current form, and a new REPL prompt to
be printed.

```bash
$ cd testproj

$ lein version
Leiningen 2.9.3 on Java 11.0.10 OpenJDK 64-Bit Server VM

$ lein repl
nREPL server started on port 34325 on host 127.0.0.1 - nrepl://127.0.0.1:34325
REPL-y 0.4.4, nREPL 0.6.0
Clojure 1.10.1
OpenJDK 64-Bit Server VM 11.0.10+9-Ubuntu-0ubuntu1.18.04
    Docs: (doc function-name-here)
          (find-doc "part-of-name-here")
  Source: (source function-name-here)
 Javadoc: (javadoc java-object-or-class-here)
    Exit: Control+D or (exit) or (quit)
 Results: Stored in vars *1, *2, *3, an exception in *e

```

```clojure
user=> (require '[testproj.mutableobj :as mo])
nil
user=> (in-ns 'testproj.mutableobj)
#object[clojure.lang.Namespace 0x7bae30c6 "testproj.mutableobj"]
```

Create a mutable object, and make some calls demonstrating that it
preserves the invariant that the sum of the balances in acct1 and
acct2 are always the same.  Object `mo1` represents two accounts with
a total balance of 100, where acct1 begins with a balance of 40, and
acct2 60.

```clojure
testproj.mutableobj=> (def mo1 (MutableObject. 100 40 60))
#'testproj.mutableobj/mo1
```

We can make a transfer of 10 from account 1 to 2 with the following
call.  The last argument of 1000 is the duration in milliseconds of a
call to the `sleep` method that is done after removing 10 from account
1, before adding 10 to account 2.  This is simply a mechanism to make
the transfer take a long time, so with normal human reaction times we
will later have a chance of interrupting this process in the middle,
after it has begun but before it completes.

```clojure
testproj.mutableobj=> (transfer-money mo1 1 10 1000)
2021-02-17T02:32:20.783891 thread Thread[nRepl-session-094969fe-5d3b-4468-916a-e1e870f56397,5,main] called transfer-money from 1 amount 10
2021-02-17T02:32:20.784176 thread Thread[nRepl-session-094969fe-5d3b-4468-916a-e1e870f56397,5,main] acquired lock from 1 amount 10
2021-02-17T02:32:21.786145 thread Thread[nRepl-session-094969fe-5d3b-4468-916a-e1e870f56397,5,main] released lock from 1 amount 10
{:acct1 30, :acct2 70}
```

The return value is a map containing the new account balances after
the transfer is complete.  All is well.

This time, we will again try to transfer 10 more from account 1 to 2,
but give a last argument of 10000, so there will be a 10-second sleep
after removing 10 from account 1 before it is added to account 2.
Type Ctrl-C during this 10 second period.

```clojure
testproj.mutableobj=> (transfer-money mo1 1 10 10000)
2021-02-17T02:35:12.334365 thread Thread[nRepl-session-094969fe-5d3b-4468-916a-e1e870f56397,5,main] called transfer-money from 1 amount 10
2021-02-17T02:35:12.334686 thread Thread[nRepl-session-094969fe-5d3b-4468-916a-e1e870f56397,5,main] acquired lock from 1 amount 10

[ I typed Ctrl-C here, about 1 second after the message above
appeared. ]

Execution error (InterruptedException) at java.lang.Thread/sleep (Thread.java:-2).
sleep interrupted

testproj.mutableobj=> 
```

The `transfer-money` function uses a JVM lock to ensure that for the
object `mo1`, at most one thread at a time can be in the critical
section of code that performs the transfer between accounts.  What we
will see below is that while that critical section did begin
execution, subtracting 10 from account 1, the `stop` method that was
executed because I typed Ctrl-C caused the thread to stop in the
middle of that critical section during the 10-second sleep, before 10
was added to account 2.

```clojure
testproj.mutableobj=> (get-balance mo1 1)
20
testproj.mutableobj=> (get-balance mo1 2)
70
testproj.mutableobj=> (total-balance mo1)
90
```

The effects of having a partially-updated mutable object, and
continuing to execute a program, can vary widely.  It could be that an
exception is thrown later, or accounting of large quantities of money
is done incorrectly, or 10% of the entries in your company's database
might be quietly changed to incorrect values over the next 3 weeks
before you discover it.  Or maybe nothing incorrect happens later at
all, e.g. if the object is never accessed again.


## If the `stop` method is so dangerous, why do developers use it?

The best answer I know is that the `stop` method is a developer
convenience -- it is usually effective at stopping JVM threads, even
if that thread is in an infinite loop.  Developers rarely want to go
to the extra effort to write code as recommended in the article linked
from the [deprecation warning for the `stop`
method](https://docs.oracle.com/javase/8/docs/api/java/lang/Thread.html#stop--),
-- direct link to that article
[here](https://docs.oracle.com/javase/8/docs/technotes/guides/concurrency/threadPrimitiveDeprecation.html).

Another reason is that while in general `stop` can leave the JVM in an
unsafe state, in many Clojure REPL sessions this does not happen.  If
you are developing mostly with pure functions that manipulate
immutable data structures, often the worst that will happen is that
some new immutable data structure that your code is in the process of
constructing will be left in an inconsistent state when a thread is
stopped.  By stopping the thread, no reference to this
partially-constructed immutable value will have been created anywhere
in the system, except inside the stopped thread, and so no other
threads can possibly access it.

Do _not_ infer from the previous paragraph that calling `stop` on a
JVM thread in a Clojure program is always safe.  Clojure code can make
calls to Java libraries that contain mutable objects, and it is not
always obvious when this is so.  Clojure library authors might not
always advertise this fact.

In case someone might wonder whether the sometimes-safety of calling
`stop` is the reason that Clojure encourages using immutable data, the
answer is no.  There are much more important reasons, such as
significantly simpler reasoning about the correctness of your
programs.


## License

Copyright © 2021 Andy Fingerhut

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
