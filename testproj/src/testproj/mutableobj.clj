(ns testproj.mutableobj)

(set! *warn-on-reflection* true)


(def log-msg-lock-obj (Object.))

(defn log-msg
  "A simple logging function that can be called concurrently from
  multiple JVM threads, since its implementation requires all callers
  to acquire a single lock (one unique to this function) before
  appending the string given by parameter `msg-str` to the log file."
  [^String msg-str]
  (locking log-msg-lock-obj
    (let [logf *out*
          now (java.time.LocalDateTime/now)]
      (.write logf (str now " "))
      (.write logf (str msg-str "\n"))
      (.flush logf))))


;; Create a toy mutable object with a very simple invariant: the sum
;; of the balances in two accounts should always be the same as it was
;; when the pair of accounts was first created.

;; The purpose for creating such an object is to demonstrate that even
;; though the code always keeps this invariant true when the `stop`
;; method is not called, it can be violated when the `stop` method is
;; called.

(definterface ITwoAccounts
  (^long getBalance [^long account-id])
  (^long totalBalance [])
  (^Object transferMoney [^long from-account-id ^long transfer-amount
                          transfer-time-millisec]))


(deftype MutableObject [^long initial-total-balance
                        ^:unsynchronized-mutable ^long acct1
                        ^:unsynchronized-mutable ^long acct2]
  ITwoAccounts
  (^long getBalance [this ^long account-id]
   (case account-id
     1 (long acct1)
     2 (long acct2)))
  
  (^long totalBalance [this]
    (+ acct1 acct2))

  (^Object transferMoney [this ^long from-account-id ^long transfer-amount
                          transfer-time-millisec]
   (let [x (.totalBalance this)]
     (when (not= x initial-total-balance)
       (throw
        (ex-info (format "Found wrong initial total balance %d - expecting %d"
                         x initial-total-balance)
                 {:acct1 acct1,
                  :acct2 acct2,
                  :initial-total-balance initial-total-balance}))))
   (case from-account-id
     1 (do
         (set! acct1 (- acct1 transfer-amount))
         (when (> transfer-time-millisec 0)
           (java.lang.Thread/sleep transfer-time-millisec))
         (set! acct2 (+ acct2 transfer-amount)))
     2 (do
         (set! acct2 (- acct2 transfer-amount))
         (when (> transfer-time-millisec 0)
           (java.lang.Thread/sleep transfer-time-millisec))
         (set! acct1 (+ acct1 transfer-amount))))
   {:acct1 acct1 :acct2 acct2}))


;; I created separate Clojure functions total-balance and
;; transfer-money below, which do the JVM locking calls, because when
;; I tried to do the locking inside of the transferMoney method above,
;; the Clojure compiler gave the error:

;;     Cannot assign to non-mutable: acct1

;; By putting the `locking` macro invocations in the functions below,
;; there was no error from the Clojure compiler.

;; As long as one never calls the methods above directly, but only the
;; functions below, the methods above will only begin execution after
;; acquiring a lock on the object.

(defn get-balance [^MutableObject mut-obj account-id]
  (locking mut-obj
    (.getBalance mut-obj account-id)))


(defn total-balance [^MutableObject mut-obj]
  (locking mut-obj
    (.totalBalance mut-obj)))


(defn transfer-money [^MutableObject mut-obj from-account-id transfer-amount
                      transfer-time-millisec]
  (let [th (java.lang.Thread/currentThread)]
    (log-msg (format "thread %s called transfer-money from %d amount %d"
                     th from-account-id transfer-amount))
    (let [final-balances
          (locking mut-obj
            (log-msg (format "thread %s acquired lock from %d amount %d"
                             th from-account-id transfer-amount))
            (.transferMoney mut-obj from-account-id transfer-amount
                            transfer-time-millisec))]
      (log-msg (format "thread %s released lock from %d amount %d"
                       th from-account-id transfer-amount))
      final-balances)))


(comment

(require '[testproj.mutableobj :as mo])
(in-ns 'testproj.mutableobj)

;; Do basic sanity testing that methods do what I expect, when called
;; one at a time from a single thread.

(def mo1 (MutableObject. 10000 4000 6000))

(get-balance mo1 1)
;; 4000
(get-balance mo1 2)
;; 6000
(total-balance mo1)
;; 10000

(transfer-money mo1 1 100 1000)
;; {:acct1 3900, :acct2 6100}

(get-balance mo1 1)
;; 3900
(get-balance mo1 2)
;; 6100
(total-balance mo1)
;; 10000

;; Try with another object that is initialized with a mismatching
;; total balance, to verify that an exception is thrown the first time
;; we try to transfer money.

(def mo2 (MutableObject. 9000 4000 6000))

(transfer-money mo2 1 100 1000)
;; Execution error (ExceptionInfo) at testproj.mutableobj.MutableObject/transferMoney (mutableobj.clj:42).
;; Found wrong initial total balance 10000 - expecting 9000

;; Check that the ex-data contained the expected values
(def e1 *e)
(ex-data e1)
;;{:acct1 4000, :acct2 6000, :initial-total-balance 9000}

;; Now try in a REPL that allows you to interrupt the evaluation of
;; the current form, such as `lein repl` on macOS and/or Linux.

(def mo3 (MutableObject. 9000 3000 6000))

;; Do not press Ctrl-C for this one.  Let it finish successfully.
(transfer-money mo3 1 100 1000)
;; 3 log messages omitted
;; {:acct1 2900, :acct2 6100}

;; This one will take 10 seconds to complete (10,000 millisec).  Abort
;; its evaluation before it finishes, e.g. using Ctrl-C in `lein
;; repl`.
(transfer-money mo3 1 100 10000)
;; 2 log messages omitted.  The 3rd should never be printed if the
;; thread was stopped.

(total-balance mo3)
;; 8900

;; 100 was removed from one account, but not added to the other.  The
;; object is now in an inconsistent state, because the previous
;; execution of transfer-money was stopped in the middle of its
;; execution.

;; Thus the sanity check should fail at the beginning of the next
;; attempt to call transfer-money, and it will throw an exception.
(transfer-money mo3 1 100 0)
;; 2 log lines omitted
Execution error (ExceptionInfo) at testproj.mutableobj.MutableObject/transferMoney (mutableobj.clj:43).
Found wrong initial total balance 8900 - expecting 9000

(def e1 *e)
(ex-data e1)
;; {:acct1 2800, :acct2 6100, :initial-total-balance 9000}

)
