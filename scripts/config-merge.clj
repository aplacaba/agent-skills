#!/usr/bin/env bb
(ns config-merge
  "Merge a Neo4j MCP server block into the global opencode config.

Reads NEO4J_URI, NEO4J_USER, NEO4J_PASSWORD, and OPENCODE_CONFIG_DIR from the
environment. Parses opencode.json(c) (JSONC: // and /* */ comments and trailing
commas tolerated), writes a .bak backup before any edit, preserves all existing
keys, and never overwrites an existing mcp.neo4j block. Exits non-zero on parse
failure. Skips (warns) when NEO4J_PASSWORD is unset."
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.string :as str]))

(defn strip-jsonc [text]
  "Remove // and /* */ comments, preserving string contents."
  (let [sb (StringBuilder.)
        n (count text)]
    (loop [i 0, in-str false]
      (when (< i n)
        (let [c (.charAt text i)
              nxt (if (< (inc i) n) (.charAt text (inc i)) nil)]
          (cond
            in-str
            (do (.append sb c)
                (if (and (= c \\) nxt)
                  (do (.append sb nxt)
                      (recur (+ i 2) true))
                  (if (= c \")
                    (recur (inc i) false)
                    (recur (inc i) true))))
            (= c \")
            (do (.append sb c)
                (recur (inc i) true))
            (and (= c \/) (= nxt \/))
            (recur (loop [j i] (if (and (< j n) (not= (.charAt text j) \newline))
                                 (recur (inc j))
                                 j)) in-str)
            (and (= c \/) (= nxt \*))
            (recur (loop [j (+ i 2)]
                     (if (and (< (inc j) n)
                              (not (and (= (.charAt text j) \*)
                                        (= (.charAt text (inc j)) \/))))
                       (recur (inc j))
                       (min (+ j 2) n))) in-str)
            :else
            (do (.append sb c)
                (recur (inc i) in-str))))))
    (.toString sb)))

(defn remove-trailing-commas [text]
  "Drop commas directly before } or ] (JSONC), preserving strings."
  (let [sb (StringBuilder.)
        n (count text)]
    (loop [i 0, in-str false]
      (when (< i n)
        (let [c (.charAt text i)]
          (cond
            in-str
            (do (.append sb c)
                (if (and (= c \\) (< (inc i) n))
                  (do (.append sb (.charAt text (inc i)))
                      (recur (+ i 2) true))
                  (if (= c \")
                    (recur (inc i) false)
                    (recur (inc i) true))))
            (= c \")
            (do (.append sb c)
                (recur (inc i) true))
            (= c \,)
            (let [j (loop [j (inc i)]
                      (if (and (< j n) (contains? #{\space \tab \newline \return} (.charAt text j)))
                        (recur (inc j))
                        j))]
              (if (and (< j n) (contains? #{\} \]} (.charAt text j)))
                (recur j in-str)
                (do (.append sb c)
                    (recur (inc i) in-str))))
            :else
            (do (.append sb c)
                (recur (inc i) in-str))))))
    (.toString sb)))

(defn find-config [config-dir]
  (or (some (fn [name]
              (let [cand (str config-dir "/" name)]
                (when (.exists (java.io.File. cand)) cand)))
            ["opencode.json" "opencode.jsonc"])
      (str config-dir "/opencode.json")))

(defn parse-config [path]
  (if (.exists (java.io.File. path))
    (let [raw (slurp path :encoding "UTF-8")]
      (try
        {:data (json/parse-string (remove-trailing-commas (strip-jsonc raw)) false)}
        (catch Exception e
          (binding [*out* *err*]
            (println (str "could not parse " path ": " (.getMessage e))))
          (System/exit 1))))
    {:data (array-map "$schema" "https://opencode.ai/config.json")}))

(defn -main [& _]
  (let [cfg-dir (or (System/getenv "OPENCODE_CONFIG_DIR") "")]
    (when (str/blank? cfg-dir)
      (binding [*out* *err*]
        (println "error: OPENCODE_CONFIG_DIR is required"))
      (System/exit 1))
    (when (str/blank? (System/getenv "NEO4J_PASSWORD"))
      (println "NEO4J_PASSWORD not set — skipping Neo4j MCP merge (set it and re-run)")
      (System/exit 0))
    (let [path (find-config cfg-dir)
          {:keys [data]} (parse-config path)
          mcp (get data "mcp")]
      (if (and mcp (contains? mcp "neo4j"))
        (println (str "  mcp.neo4j already present in " path " — leaving unchanged"))
        (do
          (when (.exists (java.io.File. path))
            (fs/copy path (str path ".bak") {:replace true})
            (println (str "  backup written: " path ".bak")))
          (let [uri (or (System/getenv "NEO4J_URI") "bolt://localhost:7687")
                user (or (System/getenv "NEO4J_USER") "neo4j")
                pwd (System/getenv "NEO4J_PASSWORD")
                new-data (assoc data
                                "$schema" (get data "$schema" "https://opencode.ai/config.json")
                                "mcp" (assoc (or mcp (array-map))
                                            "neo4j" {"type" "local"
                                                     "command" ["docker" "run" "-i" "--rm"
                                                                "-e" (str "NEO4J_URI=" uri)
                                                                "-e" (str "NEO4J_USERNAME=" user)
                                                                "-e" (str "NEO4J_PASSWORD=" pwd)
                                                                "-e" "NEO4J_DATABASE=neo4j"
                                                                "-e" "NEO4J_TRANSPORT=stdio"
                                                                "mcp/neo4j-cypher:latest"]}))]
            (spit path (str (json/generate-string new-data {:pretty true}) "\n")
                  :encoding "UTF-8")
            (println (str "  merged mcp.neo4j into " path))))))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
