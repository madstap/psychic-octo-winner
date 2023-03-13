(ns kitchen.util.files
  (:require
   [camel-snake-kebab.core :as csk]
   [clojure.data.json :as json]
   [clojure.java.io :as io]))

(defn read-json-file
  "Returns a clojure data structure parsed from the json file at `file-path`.
  Object keys are turned into camel-case keywords."
  [file-path]
  (-> (io/reader file-path)
      (json/read :key-fn csk/->kebab-case-keyword)))
