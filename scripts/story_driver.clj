#!/usr/bin/env bb
(ns story-driver
  "Helper script for the story-driven apply workflow.

Subcommands:
  parse-tasks <tasks.md> [--json]
      Parse an OpenSpec tasks.md into a structured list of task groups/tasks.
  generate <change> [--root <changeRoot>] [--def <stories.yaml>]
      Read a story definition, validate it, and write stories.md +
      story-seed.cypher into the change root.
  sync-tasks <change> <storyId> [--root <changeRoot>] [--def <stories.yaml>]
      Mark the tasks referenced by a story as done in tasks.md.
  append-state <change> <text> [--root <changeRoot>]
      Append a compact summary line to <changeRoot>/.story-state.md.
"
  (:require [cheshire.core :as json]
            [clj-yaml.core :as yaml]
            [clojure.string :as str]))

(def prog "story_driver.clj")

(def subcommands
  ["parse-tasks" "generate" "sync-tasks" "append-state"])

(defn errln [s]
  (binding [*out* *err*]
    (println s)))

(defn die [msg]
  (errln (str "error: " msg))
  (System/exit 1))

(defn die-usage [usage msg]
  (errln usage)
  (errln (str prog ": error: " msg))
  (System/exit 2))

(defn default-change-root [change root]
  (if root root (str "openspec/changes/" change)))

;; ---------------------------------------------------------------------------
;; Python-compatible helpers
;; ---------------------------------------------------------------------------

(defn py-repr-list [xs]
  (str "[" (str/join ", " (map #(str "'" % "'") xs)) "]"))

(defn indent-str [n]
  (apply str (repeat (* 2 n) \space)))

(defn py-json-escape [s]
  (let [sb (StringBuilder. "\"")]
    (doseq [c s]
      (let [n (int c)]
        (cond
          (= c \") (.append sb "\\\"")
          (= c \\) (.append sb "\\\\")
          (= c \newline) (.append sb "\\n")
          (= c \tab) (.append sb "\\t")
          (= c \return) (.append sb "\\r")
          (= c \backspace) (.append sb "\\b")
          (= c \formfeed) (.append sb "\\f")
          (< n 0x20) (.append sb (format "\\u%04x" n))
          (< n 0x80) (.append sb c)
          :else (.append sb (format "\\u%04x" n)))))
    (.append sb "\"")
    (.toString sb)))

(defn py-json-emit [x level]
  (cond
    (map? x)
    (if (empty? x)
      "{}"
      (str "{\n"
           (str/join ",\n"
                     (map (fn [[k v]]
                            (str (indent-str (inc level))
                                 (py-json-escape (name k)) ": "
                                 (py-json-emit v (inc level))))
                          x))
           "\n" (indent-str level) "}"))
    (sequential? x)
    (if (empty? x)
      "[]"
      (str "[\n"
           (str/join ",\n"
                     (map (fn [item]
                            (str (indent-str (inc level))
                                 (py-json-emit item (inc level))))
                          x))
           "\n" (indent-str level) "]"))
    (string? x) (py-json-escape x)
    (nil? x) "null"
    :else (str x)))

;; ---------------------------------------------------------------------------
;; parse-tasks
;; ---------------------------------------------------------------------------

(defn parse-tasks [tasks-path]
  (let [result
        (reduce
         (fn [acc line]
           (let [m (re-matches #"^##\s+(.+)$" line)]
             (if m
               (update acc :groups conj {:title (str/trim (second m)) :tasks []})
               (let [t (re-matches #"^-\s+\[[ xX]\]\s+(.+)$" line)]
                 (if (and t (seq (:groups acc)))
                   (update-in acc [:groups (dec (count (:groups acc))) :tasks]
                              conj {:num (inc (count (-> acc :groups last :tasks)))
                                    :desc (str/trim (second t))})
                   acc)))))
         {:groups []}
         (str/split-lines (slurp tasks-path)))]
    (let [gs (:groups result)
          all (vec (for [g gs t (:tasks g)] (:desc t)))]
      {:groups gs :all all})))

;; ---------------------------------------------------------------------------
;; story definition loading + validation
;; ---------------------------------------------------------------------------

(defn load-stories [def-path change]
  (if-not (.exists (java.io.File. def-path))
    (die (str "story definition not found: " def-path)))
  (let [data (try
               (yaml/parse-string (slurp def-path))
               (catch Exception _
                 (die (str "story definition must be a YAML mapping"))))]
    (if-not (map? data)
      (die "story definition must be a YAML mapping"))
    (if (not= (:change data) change)
      (die (str "definition change '" (:change data) "' != requested '" change "'")))
    (let [stories (vec (:stories data))]
      (if (or (not (sequential? stories)) (empty? stories))
        (die "story definition has no stories"))
      stories)))

(defn kebab? [s]
  (boolean (re-matches #"^[a-z0-9]+(-[a-z0-9]+)*$" s)))

(defn validate-stories [stories tasks]
  (let [ids (mapv :id stories)]
    (when (not= (count ids) (count (set ids)))
      (die "duplicate story ids"))
    (doseq [s stories]
      (when-not (kebab? (:id s))
        (die (str "story id '" (:id s) "' is not kebab-case")))
      (doseq [field ["title" "description"]]
        (when-not (get s (keyword field))
          (die (str "story '" (:id s) "' missing " field))))
      (let [ac (vec (:acceptanceCriteria s))]
        (when (or (not (sequential? ac)) (< (count ac) 1) (> (count ac) 3))
          (die (str "story '" (:id s) "' must have 1-3 acceptance criteria"))))
      (doseq [ref (:dependsOn s)]
        (when-not (some #{ref} ids)
          (die (str "story '" (:id s) "' depends on unknown story '" ref "'"))))
      (doseq [ref (:taskRefs s)]
        (when-not (some #{ref} tasks)
          (die (str "story '" (:id s) "' taskRef not found in tasks.md: '" ref "'")))))
    (let [covered (vec (mapcat #(vec (:taskRefs %)) stories))
          missing (vec (remove (set covered) tasks))]
      (when (seq missing)
        (die (str "tasks not covered by any story: " (py-repr-list missing)))))
    ;; Post-loop orphan check, kept for code parity (unreachable from CLI).
    (let [covered (vec (mapcat #(vec (:taskRefs %)) stories))
          orphans (vec (remove (set tasks) covered))]
      (when (seq orphans)
        (die (str "taskRefs not in tasks.md: " (py-repr-list orphans)))))
    (let [visited (atom #{})]
      (letfn [(visit [sid chain]
                (if (contains? @visited sid)
                  nil
                  (do
                    (when (some #{sid} chain)
                      (die (str "dependency cycle: "
                                (py-repr-list (conj (vec chain) sid)))))
                    (doseq [dep (:dependsOn (first (filter #(= (:id %) sid) stories)))]
                      (visit dep (conj (vec chain) sid)))
                    (swap! visited conj sid))))]
        (doseq [s stories]
          (visit (:id s) []))))))

;; ---------------------------------------------------------------------------
;; generate
;; ---------------------------------------------------------------------------

(defn cypher-str [v]
  (json/generate-string v {:escape-non-ascii false}))

(defn cypher-arr [items]
  (str "[" (str/join ", " (map cypher-str items)) "]"))

(defn write-stories-md [stories change root]
  (let [lines (atom [(str "# Stories — " change)
                     ""
                     "This file is generated by `story_driver.clj generate` — do not edit by hand."
                     ""])]
    (doseq [[i s] (map-indexed vector stories)]
      (swap! lines conj (str "## Story " (inc i) ": " (:id s) " — " (:title s)))
      (swap! lines conj "")
      (swap! lines conj (str/trim (:description s)))
      (swap! lines conj "")
      (swap! lines conj "**Acceptance criteria:**")
      (doseq [ac (:acceptanceCriteria s)]
        (swap! lines conj (str "- " ac)))
      (swap! lines conj "")
      (let [deps (vec (:dependsOn s))]
        (swap! lines conj (str "**Dependencies:** "
                               (if (seq deps) (str/join ", " deps) "none"))))
      (swap! lines conj ""))
    (spit (str root "/stories.md") (str/join "\n" @lines) :encoding "UTF-8")))

(defn write-seed [stories change project root]
  (let [parts (atom [(str "// Story graph seed for change")
                     (str "// change: " (cypher-str change))
                     (str "// project: " (cypher-str project))
                     "// Idempotent: each statement uses MERGE; safe to re-run."
                     (str "MERGE (p:Project {name: " (cypher-str project) "});")
                     (str "MERGE (c:Change {name: " (cypher-str change)
                          ", project: " (cypher-str project) "});")
                     (str "MATCH (p:Project {name: " (cypher-str project) "}),")
                     (str "      (c:Change {name: " (cypher-str change)
                          ", project: " (cypher-str project) "})")
                     "MERGE (p)-[:BELONGS_TO]->(c);"])]
    (doseq [s stories]
      (swap! parts conj "")
      (swap! parts conj (str "MERGE (s:Story {id: " (cypher-str (:id s))
                             ", change: " (cypher-str change)
                             ", project: " (cypher-str project) "})"))
      (swap! parts conj (str "ON CREATE SET s.title = " (cypher-str (:title s))
                             ", s.description = " (cypher-str (str/trim (:description s)))
                             ", s.acceptanceCriteria = " (cypher-arr (:acceptanceCriteria s))
                             ", s.taskRefs = " (cypher-arr (:taskRefs s))
                             ", s.status = \"pending\";"))
      (swap! parts conj (str "MATCH (c:Change {name: " (cypher-str change)
                             ", project: " (cypher-str project) "}),"))
      (swap! parts conj (str "      (s:Story {id: " (cypher-str (:id s))
                             ", change: " (cypher-str change)
                             ", project: " (cypher-str project) "})"))
      (swap! parts conj "MERGE (c)-[:HAS_STORY]->(s);"))
    (doseq [s stories]
      (doseq [dep (:dependsOn s)]
        (swap! parts conj "")
        (swap! parts conj (str "MATCH (a:Story {id: " (cypher-str (:id s))
                               ", change: " (cypher-str change)
                               ", project: " (cypher-str project) "})"))
        (swap! parts conj (str "MATCH (b:Story {id: " (cypher-str dep)
                               ", change: " (cypher-str change)
                               ", project: " (cypher-str project) "})"))
        (swap! parts conj "MERGE (a)-[:DEPENDS_ON]->(b);")))
    (spit (str root "/story-seed.cypher") (str/join "\n" @parts) :encoding "UTF-8")))

;; ---------------------------------------------------------------------------
;; sync-tasks / append-state
;; ---------------------------------------------------------------------------

(defn sync-tasks [change story-id root def-path]
  (let [stories (load-stories def-path change)
        story (first (filter #(= (:id %) story-id) stories))]
    (when (nil? story)
      (die (str "unknown story '" story-id "' in " def-path)))
    (let [tasks-path (str root "/tasks.md")]
      (when-not (.exists (java.io.File. tasks-path))
        (die (str "tasks.md not found: " tasks-path)))
      (let [refs (set (:taskRefs story))
            text (slurp tasks-path)
            trailing-nl (str/ends-with? text "\n")
            lines (str/split-lines text)
            toggled (atom 0)
            out (mapv (fn [line]
                        (let [m (re-matches #"^(-\s+)\[ \]\s+(.*)$" line)]
                          (if (and m (contains? refs (str/trim (nth m 2))))
                            (do (swap! toggled inc)
                                (str (nth m 1) "[x] " (nth m 2)))
                            line)))
                      lines)]
        (spit tasks-path (str (str/join "\n" out) (if trailing-nl "\n" ""))
              :encoding "UTF-8")
        (println (str "toggled " @toggled " task(s) for story " story-id))))))

(defn append-state [change text root]
  (let [state-path (str root "/.story-state.md")]
    (when-not (.exists (java.io.File. state-path))
      (spit state-path (str "# Story state — " change "\n") :encoding "UTF-8"))
    (spit state-path (str "- " text "\n") :encoding "UTF-8" :append true)
    (println (str "appended to " state-path))))

;; ---------------------------------------------------------------------------
;; CLI
;; ---------------------------------------------------------------------------

(def root-usage
  (str "usage: " prog " [-h] {" (str/join "," subcommands) "} ..."))

(def sub-usage
  {"parse-tasks"  (str "usage: " prog " parse-tasks [-h] tasks [--json]")
   "generate"     (str "usage: " prog " generate [-h] change [--root ROOT] [--def DEF] --project PROJECT")
   "sync-tasks"   (str "usage: " prog " sync-tasks [-h] change story_id [--root ROOT] [--def DEF]")
   "append-state" (str "usage: " prog " append-state [-h] change text [--root ROOT]")})

(def sub-help
  {"parse-tasks"  "Parse an OpenSpec tasks.md into a structured list of task groups/tasks."
   "generate"     "Read a story definition, validate it, and write stories.md + story-seed.cypher into the change root."
   "sync-tasks"   "Mark the tasks referenced by a story as done in tasks.md."
   "append-state" "Append a compact summary line to <changeRoot>/.story-state.md."})

(def root-help
  (str "usage: " prog " [-h] {" (str/join "," subcommands) "} ...\n\n"
       "Helper script for the story-driven apply workflow.\n\n"
       "Subcommands:\n"
       "  parse-tasks <tasks.md> [--json]\n"
       "      Parse an OpenSpec tasks.md into a structured list of task groups/tasks.\n"
       "  generate <change> [--root <changeRoot>] [--def <stories.yaml>] --project <name>\n"
       "      Read a story definition, validate it, and write stories.md +\n"
       "      story-seed.cypher into the change root (scoped to the project).\n"
       "  sync-tasks <change> <storyId> [--root <changeRoot>] [--def <stories.yaml>]\n"
       "      Mark the tasks referenced by a story as done in tasks.md.\n"
       "  append-state <change> <text> [--root <changeRoot>]\n"
       "      Append a compact summary line to <changeRoot>/.story-state.md.\n\n"
       "options:\n"
       "  -h, --help  show this help message and exit"))

(defn print-sub-help [sub]
  (println (sub-usage sub))
  (println)
  (println (sub-help sub))
  (println)
  (println "options:")
  (println "  -h, --help  show this help message and exit")
  (System/exit 0))

(defn parse-args [sub args]
  "Parse per-subcommand args. Returns {:positionals [...] :opts {...}} or exits with usage."
  (let [spec {"parse-tasks"  {:flags #{:json} :positionals ["tasks"] :npos 1}
              "generate"     {:flags #{:root :def :project} :positionals ["change"] :npos 1}
              "sync-tasks"   {:flags #{:root :def} :positionals ["change" "story_id"] :npos 2}
              "append-state" {:flags #{:root} :positionals ["change" "text"] :npos 2}}
        {:keys [flags positionals npos]} (spec sub)
        flag-tokens {"--json" :json "--root" :root "--def" :def "--project" :project}
        required-flags {"generate" #{:project}}]
    (loop [toks args, pos [], opts {}]
      (if (empty? toks)
        (do
          (when (< (count pos) npos)
            (die-usage (sub-usage sub)
                       (str "the following arguments are required: "
                            (str/join ", " (drop (count pos) positionals)))))
          (when (> (count pos) npos)
            (die-usage (sub-usage sub)
                       (str "unrecognized arguments: " (str/join " " (drop npos pos)))))
          (let [missing (remove #(contains? opts %) (required-flags sub))]
            (when (seq missing)
              (die-usage (sub-usage sub)
                         (str "the following arguments are required: "
                              (str/join ", " (map #(str "--" (name %)) missing))))))
          {:positionals pos :opts opts})
        (let [tok (first toks)
              more (rest toks)]
          (cond
            (contains? #{"-h" "--help"} tok)
            (print-sub-help sub)
            (contains? flag-tokens tok)
            (let [k (flag-tokens tok)]
              (if (contains? flags k)
                (if (= k :json)
                  (recur more pos (assoc opts k true))
                  (if (or (empty? more) (str/starts-with? (second toks) "-"))
                    (die-usage (sub-usage sub)
                               (str "argument " tok ": expected one argument"))
                    (recur (nnext toks) pos (assoc opts k (second toks)))))
                (die-usage (sub-usage sub)
                           (str "unrecognized arguments: " tok))))
            (str/starts-with? tok "-")
            (die-usage (sub-usage sub) (str "unrecognized arguments: " tok))
            :else
            (recur more (conj pos tok) opts)))))))

(defn cmd-parse-tasks [args]
  (let [{:keys [positionals opts]} (parse-args "parse-tasks" args)
        data (parse-tasks (first positionals))]
    (if (:json opts)
      (println (py-json-emit (array-map :groups (mapv (fn [g]
                                                        (array-map :title (:title g)
                                                                   :tasks (mapv (fn [t]
                                                                                  (array-map :num (:num t)
                                                                                             :desc (:desc t)))
                                                                                (:tasks g))))
                                                      (:groups data))
                                       :all (:all data))
                             0))
      (doseq [g (:groups data)]
        (println (str "## " (:title g)))
        (doseq [t (:tasks g)]
          (println (str "- [ ] " (:desc t))))))))

(defn cmd-generate [args]
  (let [{:keys [positionals opts]} (parse-args "generate" args)
        change (first positionals)
        project (:project opts)
        root (default-change-root change (:root opts))
        def-path (:def opts)
        def-path (if def-path def-path (str root "/stories.yaml"))
        tasks-path (str root "/tasks.md")]
    (when-not (.exists (java.io.File. tasks-path))
      (die (str "tasks.md not found: " tasks-path)))
    (let [tasks (:all (parse-tasks tasks-path))
          stories (load-stories def-path change)]
      (validate-stories stories tasks)
      (write-stories-md stories change root)
      (write-seed stories change project root)
      (println (str "wrote " root "/stories.md"))
      (println (str "wrote " root "/story-seed.cypher")))))

(defn cmd-sync [args]
  (let [{:keys [positionals opts]} (parse-args "sync-tasks" args)
        change (first positionals)
        story-id (second positionals)
        root (default-change-root change (:root opts))
        def-path (:def opts)
        def-path (if def-path def-path (str root "/stories.yaml"))]
    (sync-tasks change story-id root def-path)))

(defn cmd-append [args]
  (let [{:keys [positionals opts]} (parse-args "append-state" args)
        change (first positionals)
        text (second positionals)
        root (default-change-root change (:root opts))]
    (append-state change text root)))

(defn -main [& args]
  (cond
    (empty? args)
    (do (errln root-usage)
        (errln (str prog ": error: the following arguments are required: command"))
        (System/exit 2))
    (contains? #{"-h" "--help"} (first args))
    (do (println root-help)
        (System/exit 0))
    (not (some #{(first args)} subcommands))
    (do (errln root-usage)
        (errln (str prog ": error: argument command: invalid choice: '"
                    (first args) "' (choose from "
                    (str/join ", " (map #(str "'" % "'") subcommands)) ")"))
        (System/exit 2))
    :else
    (case (first args)
      "parse-tasks" (cmd-parse-tasks (rest args))
      "generate" (cmd-generate (rest args))
      "sync-tasks" (cmd-sync (rest args))
      "append-state" (cmd-append (rest args)))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
