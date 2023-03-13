(ns kitchen.log)

(defn stdout-log [event data]
  ;; Pass a single string arg to avoid interleaving concurrent prints
  (print (str event " " (pr-str data) "\n")))
