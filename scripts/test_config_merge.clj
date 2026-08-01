#!/usr/bin/env bb
(ns test-config-merge
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

(def script (str (fs/canonicalize "scripts/config-merge.clj")))

(defn run-with [env args]
  (let [full-env (merge {"PATH" (System/getenv "PATH")
                         "HOME" (System/getenv "HOME")}
                        env)
        cmd (concat [{:out :string :err :string :continue true :env full-env}]
                    ["bb" script] args)
        proc (apply p/process cmd)]
    @proc))

(defn setup-dir [files]
  (let [d (str (fs/create-temp-dir))]
    (doseq [[name content] files]
      (spit (str d "/" name) content :encoding "UTF-8"))
    d))

(defn env-for [d]
  {"OPENCODE_CONFIG_DIR" d
   "NEO4J_URI" "bolt://localhost:7687"
   "NEO4J_USER" "neo4j"
   "NEO4J_PASSWORD" "secret"})

(deftest jsonc-parsing
  (testing "comments and trailing commas are stripped, strings preserved"
    (let [d (setup-dir [["opencode.json"
                         (str "{ // line comment\n"
                              "  \"$schema\": \"https://opencode.ai/config.json\",\n"
                              "  \"mcp\": {\n"
                              "    \"other\": \"a//b\",\n"
                              "    \"blocked\": \"/* not a comment */\",\n"
                              "    \"trailing\": \"x\", // trailing comment\n"
                              "  },\n"
                              "}\n")]])
          r (run-with (env-for d) [])]
      (is (= 0 (:exit r)))
      (let [data (json/parse-string (slurp (str d "/opencode.json")) true)]
        (is (= "a//b" (get-in data [:mcp :other])))
        (is (= "/* not a comment */" (get-in data [:mcp :blocked])))
        (is (= "x" (get-in data [:mcp :trailing])))
        (is (get-in data [:mcp :neo4j]))))))

(deftest malformed-config
  (testing "malformed config exits non-zero with could not parse prefix on stderr"
    (let [d (setup-dir [["opencode.json" "{ bad json"]])
          r (run-with (env-for d) [])]
      (is (= 1 (:exit r)))
      (is (str/starts-with? (:err r) "could not parse ")))))

(deftest new-config-creation
  (testing "no config file creates one with merged mcp.neo4j"
    (let [d (setup-dir [])
          r (run-with (env-for d) [])]
      (is (= 0 (:exit r)))
      (let [data (json/parse-string (slurp (str d "/opencode.json")) true)]
        (is (get-in data [:mcp :neo4j]))
        (is (= ["docker" "run" "-i" "--rm"
                "-e" "NEO4J_URI=bolt://localhost:7687"
                "-e" "NEO4J_USERNAME=neo4j"
                "-e" "NEO4J_PASSWORD=secret"
                "-e" "NEO4J_DATABASE=neo4j"
                "-e" "NEO4J_TRANSPORT=stdio"
                "mcp/neo4j-cypher:latest"]
               (get-in data [:mcp :neo4j :command])))))))

(deftest preserves-unrelated-keys
  (testing "existing keys are preserved across a merge"
    (let [d (setup-dir [["opencode.json"
                         "{\"theme\": \"dark\", \"mcp\": {\"github\": {\"type\": \"remote\"}}}\n"]])
          r (run-with (env-for d) [])]
      (is (= 0 (:exit r)))
      (let [data (json/parse-string (slurp (str d "/opencode.json")) true)]
        (is (= "dark" (:theme data)))
        (is (get-in data [:mcp :github]))
        (is (get-in data [:mcp :neo4j]))))))

(deftest json-over-jsonc
  (testing "opencode.json is preferred over opencode.jsonc"
    (let [d (setup-dir [["opencode.json" "{\"json\": true}\n"]
                        ["opencode.jsonc" "{\"jsonc\": true}\n"]])
          r (run-with (env-for d) [])]
      (is (= 0 (:exit r)))
      (let [data (json/parse-string (slurp (str d "/opencode.json")) true)]
        (is (:json data))
        (is (nil? (:jsonc data)))
        (is (= "{\"jsonc\": true}" (str/trim (slurp (str d "/opencode.jsonc")))))))))

(deftest existing-block-preserved
  (testing "existing neo4j block is left unchanged and no backup is written"
    (let [d (setup-dir [["opencode.json"
                         "{\"mcp\": {\"neo4j\": {\"type\": \"custom\", \"custom\": true}}}\n"]])
          before (slurp (str d "/opencode.json"))
          r (run-with (env-for d) [])]
      (is (= 0 (:exit r)))
      (is (str/includes? (:out r) "leaving unchanged"))
      (is (= before (slurp (str d "/opencode.json"))))
      (is (not (.exists (java.io.File. (str d "/opencode.json.bak"))))))))

(deftest backup-on-edit
  (testing "a merge that edits writes a backup"
    (let [d (setup-dir [["opencode.json" "{\"theme\": \"dark\"}\n"]])
          r (run-with (env-for d) [])]
      (is (= 0 (:exit r)))
      (is (.exists (java.io.File. (str d "/opencode.json.bak"))))
      (is (= "{\"theme\": \"dark\"}"
             (str/trim (slurp (str d "/opencode.json.bak"))))))))

(deftest skip-without-password
  (testing "no NEO4J_PASSWORD skips the merge"
    (let [d (setup-dir [["opencode.json" "{\"theme\": \"dark\"}\n"]])
          r (run-with {"OPENCODE_CONFIG_DIR" d} [])]
      (is (= 0 (:exit r)))
      (is (str/includes? (:out r) "skipping Neo4j MCP merge"))
      (is (= "{\"theme\": \"dark\"}" (str/trim (slurp (str d "/opencode.json"))))))))

(let [result (clojure.test/run-tests)]
  (System/exit (if (and (zero? (:fail result)) (zero? (:error result))) 0 1)))
