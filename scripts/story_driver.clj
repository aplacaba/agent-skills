#!/usr/bin/env bb
(ns story-driver
  "Helper script for the story-driven apply workflow.

Subcommands:
  parse-tasks <tasks.md> [--json]
      Parse an OpenSpec tasks.md into a structured list of task groups/tasks.
  generate <change> [--root <changeRoot>] [--def <stories.yaml>] --project <name> [--vault <path>]
      Read a story definition, validate it, and write stories.md into the
      change root plus story notes into the Obsidian vault.
  next <change> --project <name> [--vault <path>]
      Print the next runnable story as JSON (plus counts/blocked/in-progress).
  set-status <change> <storyId> <status> --project <name> [--vault <path>]
      Rewrite a story note's status frontmatter field.
  classify <project> [--type <t>] [--tech-stack <a,b>] [--repo-url <u>] [--vault <path>]
      Write project classification frontmatter.
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
  ["parse-tasks" "generate" "next" "set-status" "classify" "sync-tasks" "append-state"])

(def valid-statuses #{"pending" "in_progress" "done"})

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
;; Vault resolution, slugs, atomic writes, frontmatter
;; ---------------------------------------------------------------------------

(defn resolve-vault [flag]
  (or flag
      (System/getenv "OBSIDIAN_VAULT")
      (str (System/getenv "HOME") "/obsidian/obsidian")))

(defn check-vault! [vault]
  (let [f (java.io.File. vault)]
    (when-not (.isDirectory f)
      (die (str "vault root not found: " vault " (set OBSIDIAN_VAULT or pass --vault)")))
    (when-not (.canWrite f)
      (die (str "vault root not writable: " vault)))))

(defn slug [s]
  (-> (str s)
      str/lower-case
      str/trim
      (str/replace #"[^a-z0-9._-]+" "-")
      (str/replace #"-{2,}" "-")
      (str/replace #"^-|-$" "")))

(defn atomic-spit [path content]
  (let [f (java.io.File. path)]
    (when-not (.exists (.getParentFile f))
      (.mkdirs (.getParentFile f)))
    (let [tmp (str path ".tmp-" (System/nanoTime))
          to (.toPath f)
          from (.toPath (java.io.File. tmp))]
      (spit tmp content :encoding "UTF-8")
      (try
        (java.nio.file.Files/move from to
          (into-array java.nio.file.CopyOption
                      [java.nio.file.StandardCopyOption/REPLACE_EXISTING
                       java.nio.file.StandardCopyOption/ATOMIC_MOVE]))
        (catch Exception e
          (if (str/includes? (str e) "Atomic move not supported")
            (java.nio.file.Files/move from to
              (into-array java.nio.file.CopyOption
                          [java.nio.file.StandardCopyOption/REPLACE_EXISTING]))
            (throw e)))))))

(defn split-frontmatter [content]
  "Returns {:fm {...} :body \"...\"} or nil when the note has no frontmatter.
  The body is the exact text after the closing --- line."
  (let [lines (str/split content #"\n" -1)]
    (when (= "---" (first lines))
      (let [rest-lines (vec (rest lines))
            closing-idx (first (keep-indexed (fn [i l] (when (= "---" l) i)) rest-lines))]
        (when closing-idx
          (let [fm-text (str/join "\n" (take closing-idx rest-lines))
                parsed (try (yaml/parse-string fm-text) (catch Exception _ nil))]
            {:fm (if (map? parsed) parsed {})
             :body (str/join "\n" (drop (inc closing-idx) rest-lines))}))))))

(defn yaml-scalar [v]
  (cond
    (string? v) (json/generate-string v {:escape-non-ascii false})
    (or (sequential? v) (map? v) (nil? v) (number? v) (boolean? v))
    (json/generate-string v {:escape-non-ascii false})
    :else (json/generate-string (str v) {:escape-non-ascii false})))

(defn emit-note [fm body]
  (str "---\n"
       (str/join "\n" (map (fn [[k v]] (str (name k) ": " (yaml-scalar v))) fm))
       "\n---\n"
       body))

(defn read-note [path]
  (when (.isFile (java.io.File. path))
    (split-frontmatter (slurp path :encoding "UTF-8"))))

(defn note-status [note]
  (str (or (:status (:fm note)) "pending")))

(defn section [body name]
  "Returns the text of body's '## <name>' section (without the header line), or nil."
  (let [pat (re-pattern (str "(?m)^## " (java.util.regex.Pattern/quote name) "[ \\t]*\n"))
        m (re-find pat body)]
    (when m
      (let [start (+ (.indexOf body m) (count m))
            rest (subs body start)
            next-h (.indexOf rest "\n## ")]
        (subs rest 0 (if (neg? next-h) (count rest) next-h))))))

(defn bullets [s]
  (->> (str/split s #"\n" -1)
       (keep #(when-let [m (re-matches #"^-\s+(.+)$" %)] (second m)))))

(defn dep-ids [body]
  (into #{} (map (fn [[_ link]]
                   (let [[path alias] (str/split link #"\|" 2)]
                     (if alias alias (last (str/split path #"/"))))))
        (re-seq (re-pattern "- \\[\\[([^\\]]+)\\]\\]") body)))

(defn description-portion [body]
  (let [idx (.indexOf body "\n## ")]
    (str/trim (if (neg? idx) body (subs body 0 idx)))))

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

(defn merge-changes-link [body p-slug c-slug change]
  (let [line (str "- [[Stories/" p-slug "/" c-slug "/_change|" change "]]")]
    (if (str/includes? body line)
      body
      (let [m (re-find #"(?m)^## Changes[ \t]*$" body)]
        (if m
          (let [pos (+ (.indexOf body m) (count m))
                before (subs body 0 pos)
                after (subs body pos)]
            (str before "\n" line after))
          (str body
               (when-not (str/ends-with? body "\n") "\n")
               "\n## Changes\n" line "\n"))))))

(defn write-story-note [vault p-slug c-slug s change project]
  (let [path (str vault "/Stories/" p-slug "/" c-slug "/" (slug (str (:id s))) ".md")
        existing (read-note path)
        status (note-status existing)]
    (when-not (contains? valid-statuses status)
      (die (str "corrupted status '" status "' in " path)))
    (let [deps (vec (:dependsOn s))
          body (str (str/trim (:description s)) "\n\n"
                    (when (seq (:acceptanceCriteria s))
                      (str "## Acceptance criteria\n"
                           (str/join "\n" (map #(str "- " %) (:acceptanceCriteria s)))
                           "\n\n"))
                    (when (seq deps)
                      (str "## Depends on\n"
                           (str/join "\n" (map (fn [d] (str "- [[Stories/" p-slug "/" c-slug "/" (slug (str d)) "|" d "]]")) deps))
                           "\n\n"))
                    (when (seq (:taskRefs s))
                      (str "## Tasks\n"
                           (str/join "\n" (map #(str "- " %) (:taskRefs s)))
                           "\n")))
          fm (array-map :id (str (:id s)) :title (:title s) :change change
                        :project project :status status)]
      (atomic-spit path (emit-note fm body)))))

(defn write-change-note [vault p-slug c-slug change project stories]
  (let [path (str vault "/Stories/" p-slug "/" c-slug "/_change.md")
        existing (read-note path)]
    (when (and existing
               (or (not= (str (:name (:fm existing))) (str change))
                   (not= (str (:project (:fm existing))) (str project))))
      (die (str "change slug collision: '" change "' vs existing '"
                (:name (:fm existing)) "' at " path)))
    (let [fm (array-map :name change :project project)
          body (str "# " change "\n\n## Stories\n"
                    (str/join "\n" (map (fn [s] (str "- [[" (slug (str (:id s))) "|" (:title s) "]]")) stories))
                    "\n")]
      (atomic-spit path (emit-note fm body)))))

(defn write-project-note [vault project p-slug c-slug change]
  (let [path (str vault "/Projects/" p-slug ".md")
        existing (read-note path)]
    (when (and existing (not= (str (:name (:fm existing))) (str project)))
      (die (str "project slug collision: '" project "' vs existing '"
                (:name (:fm existing)) "' at " path)))
    (let [fm (if existing
               (assoc (:fm existing) :name project)
               (array-map :name project :type nil :techStack nil :repoUrl nil))
          body (if existing
                 (merge-changes-link (:body existing) p-slug c-slug change)
                 (merge-changes-link (str "# " project "\n") p-slug c-slug change))]
      (atomic-spit path (emit-note fm body)))))

(defn write-vault-notes [stories change project vault]
  (let [p-slug (slug project)
        c-slug (slug change)]
    (write-change-note vault p-slug c-slug change project stories)
    (doseq [s stories]
      (write-story-note vault p-slug c-slug s change project))
    (write-project-note vault project p-slug c-slug change)))

;; ---------------------------------------------------------------------------
;; next / set-status / classify
;; ---------------------------------------------------------------------------

(declare parse-args)

(defn guard-change-folder! [vault p-slug c-slug change project]
  (let [dir (str vault "/Stories/" p-slug "/" c-slug)]
    (when-not (.isDirectory (java.io.File. dir))
      (die (str "change folder not found: " dir " (run generate first)")))
    (let [cn (read-note (str dir "/_change.md"))]
      (when (and cn
                 (or (not= (str (:name (:fm cn))) (str change))
                     (not= (str (:project (:fm cn))) (str project))))
        (die (str "change slug collision: '" change "' vs existing '"
                  (:name (:fm cn)) "' at " dir))))
    dir))

(defn cmd-next [args]
  (let [{:keys [positionals opts]} (parse-args "next" args)
        change (first positionals)
        project (:project opts)
        vault (resolve-vault (:vault opts))]
    (check-vault! vault)
    (let [dir (guard-change-folder! vault (slug project) (slug change) change project)
          files (->> (file-seq (java.io.File. dir))
                     (filter #(and (.isFile %)
                                   (str/ends-with? (.getName %) ".md")
                                   (not= (.getName %) "_change.md")))
                     (map (fn [f]
                            (let [n (read-note (.getPath f))
                                  name (re-find #"([^/]+)\.md$" (.getPath f))]
                              {:id (or (:id (:fm n)) (second name))
                               :fm (:fm n)
                               :body (:body n)})))
                     (sort-by :id)
                     vec)]
      (doseq [f files]
        (when-not (contains? valid-statuses (note-status f))
          (die (str "corrupted status '" (note-status f) "' in story note '" (:id f) "'"))))
      (let [statuses (into {} (map (fn [f] [(:id f) (note-status f)]) files))
            runnable? (fn [f]
                        (and (= "pending" (note-status f))
                             (every? #(= "done" (statuses % "pending"))
                                     (dep-ids (:body f)))))
            runnable (first (filter runnable? files))
            done (count (filter #(= "done" (note-status %)) files))
            in-progress (filter #(= "in_progress" (note-status %)) files)
            pending (filter #(= "pending" (note-status %)) files)
            blocked (->> pending (remove runnable?) (mapv :id))
            out {:runnable (when runnable
                             (array-map
                              :id (:id runnable)
                              :title (:title (:fm runnable))
                              :description (description-portion (:body runnable))
                              :acceptanceCriteria (vec (bullets (or (section (:body runnable) "Acceptance criteria") "")))
                              :taskRefs (vec (bullets (or (section (:body runnable) "Tasks") "")))))
                 :counts {:total (count files)
                          :done done
                          :inProgress (count in-progress)
                          :pending (count pending)
                          :remaining (- (count files) done)}
                 :blocked blocked
                 :inProgressIds (mapv :id in-progress)}]
        (println (json/generate-string out {:escape-non-ascii false}))))))

(defn cmd-set-status [args]
  (let [{:keys [positionals opts]} (parse-args "set-status" args)
        change (first positionals)
        story-id (second positionals)
        status (nth positionals 2)
        project (:project opts)
        vault (resolve-vault (:vault opts))]
    (check-vault! vault)
    (when-not (contains? valid-statuses status)
      (die (str "invalid status '" status "' (choose from pending, in_progress, done)")))
    (let [dir (guard-change-folder! vault (slug project) (slug change) change project)
          path (str dir "/" (slug story-id) ".md")
          note (read-note path)]
      (when-not note
        (die (str "story note not found: " path)))
      (when (or (not= (str (:change (:fm note))) (str change))
                (not= (str (:project (:fm note))) (str project)))
        (die (str "story note identity mismatch: '" story-id "' belongs to change '"
                  (:change (:fm note)) "' project '" (:project (:fm note)) "'")))
      (atomic-spit path (emit-note (assoc (:fm note) :status status) (:body note)))
      (println (str "set status " status " for " story-id)))))

(defn cmd-classify [args]
  (let [{:keys [positionals opts]} (parse-args "classify" args)
        project (first positionals)
        vault (resolve-vault (:vault opts))
        p-slug (slug project)
        path (str vault "/Projects/" p-slug ".md")]
    (check-vault! vault)
    (let [existing (read-note path)]
      (when (and existing (not= (str (:name (:fm existing))) (str project)))
        (die (str "project slug collision: '" project "' vs existing '"
                  (:name (:fm existing)) "' at " path)))
      (let [tech (when (seq (:tech-stack opts))
                   (vec (map str/trim (str/split (:tech-stack opts) #","))))
            fm (-> (or (:fm existing) (array-map :name project))
                   (assoc :name project
                          :type (or (:type opts) nil)
                          :techStack (or tech nil)
                          :repoUrl (or (:repo-url opts) nil)))
            body (if existing (:body existing) (str "# " project "\n"))]
        (atomic-spit path (emit-note fm body))
        (println (str "classified " project))))))

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
   "generate"     (str "usage: " prog " generate [-h] change [--root ROOT] [--def DEF] --project PROJECT [--vault VAULT]")
   "next"         (str "usage: " prog " next [-h] change --project PROJECT [--vault VAULT]")
   "set-status"   (str "usage: " prog " set-status [-h] change story_id status --project PROJECT [--vault VAULT]")
   "classify"     (str "usage: " prog " classify [-h] project [--type TYPE] [--tech-stack LIST] [--repo-url URL] [--vault VAULT]")
   "sync-tasks"   (str "usage: " prog " sync-tasks [-h] change story_id [--root ROOT] [--def DEF]")
   "append-state" (str "usage: " prog " append-state [-h] change text [--root ROOT]")})

(def sub-help
  {"parse-tasks"  "Parse an OpenSpec tasks.md into a structured list of task groups/tasks."
   "generate"     "Read a story definition, validate it, and write stories.md + vault story notes."
   "next"         "Print the next runnable story as JSON (with counts, blocked and in-progress ids)."
   "set-status"   "Rewrite a story note's status frontmatter field (pending, in_progress, done)."
   "classify"     "Write project classification frontmatter (type, techStack, repoUrl)."
   "sync-tasks"   "Mark the tasks referenced by a story as done in tasks.md."
   "append-state" "Append a compact summary line to <changeRoot>/.story-state.md."})

(def root-help
  (str "usage: " prog " [-h] {" (str/join "," subcommands) "} ...\n\n"
       "Helper script for the story-driven apply workflow.\n\n"
       "Subcommands:\n"
       "  parse-tasks <tasks.md> [--json]\n"
       "      Parse an OpenSpec tasks.md into a structured list of task groups/tasks.\n"
       "  generate <change> [--root <changeRoot>] [--def <stories.yaml>] --project <name> [--vault <path>]\n"
       "      Read a story definition, validate it, and write stories.md +\n"
       "      vault story notes into the Obsidian vault (scoped to the project).\n"
       "  next <change> --project <name> [--vault <path>]\n"
       "      Print the next runnable story as JSON (plus counts/blocked/in-progress).\n"
       "  set-status <change> <storyId> <status> --project <name> [--vault <path>]\n"
       "      Rewrite a story note's status frontmatter field.\n"
       "  classify <project> [--type <t>] [--tech-stack <a,b>] [--repo-url <u>] [--vault <path>]\n"
       "      Write project classification frontmatter.\n"
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
              "generate"     {:flags #{:root :def :project :vault} :positionals ["change"] :npos 1}
              "next"         {:flags #{:project :vault} :positionals ["change"] :npos 1}
              "set-status"   {:flags #{:project :vault} :positionals ["change" "story_id" "status"] :npos 3}
              "classify"     {:flags #{:type :tech-stack :repo-url :vault} :positionals ["project"] :npos 1}
              "sync-tasks"   {:flags #{:root :def} :positionals ["change" "story_id"] :npos 2}
              "append-state" {:flags #{:root} :positionals ["change" "text"] :npos 2}}
        {:keys [flags positionals npos]} (spec sub)
        flag-tokens {"--json" :json "--root" :root "--def" :def "--project" :project
                     "--vault" :vault "--type" :type "--tech-stack" :tech-stack
                     "--repo-url" :repo-url}
        required-flags {"generate" #{:project}
                        "next" #{:project}
                        "set-status" #{:project}}]
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
        tasks-path (str root "/tasks.md")
        vault (resolve-vault (:vault opts))]
    (check-vault! vault)
    (when-not (.exists (java.io.File. tasks-path))
      (die (str "tasks.md not found: " tasks-path)))
    (let [tasks (:all (parse-tasks tasks-path))
          stories (load-stories def-path change)]
      (validate-stories stories tasks)
      (write-stories-md stories change root)
      (write-vault-notes stories change project vault)
      (println (str "wrote " root "/stories.md"))
      (println (str "wrote vault notes for " change " (project " project ")")))))

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
      "next" (cmd-next (rest args))
      "set-status" (cmd-set-status (rest args))
      "classify" (cmd-classify (rest args))
      "sync-tasks" (cmd-sync (rest args))
      "append-state" (cmd-append (rest args)))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
