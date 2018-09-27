(ns midje-nrepl.project-info
  (:require [clojure.java.io :as io]
            [clojure.tools.namespace.find :as namespace.find])
  (:import (java.io FileReader PushbackReader)))

(def ^:private leiningen-project-file "project.clj")

(defn- project-working-dir []
  (.getCanonicalFile (io/file ".")))

(defn read-leiningen-project []
  (let [project-file (io/file (project-working-dir) leiningen-project-file)]
    (with-open [reader (PushbackReader. (FileReader. project-file))]
      (read reader))))

(defn read-project-map []
  (let [[_ project-name version & others] (read-leiningen-project)]
    (into {:project-name+version [project-name version]}
          (apply hash-map others))))

(defn existing-dir? [candidate]
  (.isDirectory (io/file (project-working-dir) candidate)))

(defn get-test-paths []
  (let [project-map (read-project-map)]
    (->> (get project-map :test-paths ["test"])
         (filter existing-dir?)
         sort)))

(defn get-test-namespaces-in [test-paths]
  (->> test-paths
       (map (partial io/file (project-working-dir)))
       (mapcat namespace.find/find-namespaces-in-dir)
       sort))