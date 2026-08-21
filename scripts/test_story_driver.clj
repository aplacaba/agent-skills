#!/usr/bin/env bb
(ns test-story-driver
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

(def script (str (fs/canonicalize "scripts/story_driver.clj")))
(def fixtures-dir (str (fs/canonicalize "scripts/test/fixtures")))
(def golden-dir (str fixtures-dir "/golden"))
(def golden-vault (str golden-dir "/vault"))
(def fixture-root (str fixtures-dir "/change-root"))

(defn tmp-root []
  (let [d (str (fs/create-temp-dir))]
    (fs/copy-tree fixture-root (str d "/change-root"))
    (fs/create-dirs (str d "/vault"))
    {:dir d
     :root (str d "/change-root")
     :vault (str d "/vault")}))

(defn clean-env [overrides]
  (-> (into {} (System/getenv))
      (dissoc "OBSIDIAN_VAULT")
      (merge overrides)))

(defn run [& args]
  (let [env (if (map? (first args)) (first args) {})
        cmd-args (if (map? (first args)) (rest args) args)
        cmd-args (if (and (= 1 (count cmd-args)) (sequential? (first cmd-args)))
                   (first cmd-args) cmd-args)
        proc (apply p/process (concat [{:out :string :err :string :continue true
                                        :env (clean-env env)}]
                                      ["bb" script] cmd-args))]
    @proc))
(defn file->str [path]
  (slurp path :encoding "UTF-8"))

(defn vault-tree [vault]
  (into {}
        (map (fn [f]
               (let [p (str f)]
                 [(subs p (inc (count vault))) (file->str p)])))
        (filter #(.isFile %) (file-seq (java.io.File. vault)))))

(defn body-of [content]
  (let [i1 (.indexOf content "---\n")
        i2 (.indexOf content "---\n" (+ i1 3))]
    (subs content (+ i2 4))))

(defn gen [t]
  (run {"HOME" (str (:dir t) "/home")}
       ["generate" "fixture-change" "--project" "fixture-project"
        "--root" (:root t) "--def" (str (:root t) "/stories.yaml")
        "--vault" (:vault t)]))

(defn next-json [t & [change project]]
  (let [r (run ["next" (or change "fixture-change")
                "--project" (or project "fixture-project")
                "--vault" (:vault t)])]
    (assoc r :data (when (= 0 (:exit r))
                     (json/parse-string (:out r) true)))))

(defn classify [t & args]
  (apply run (concat ["classify" "fixture-project" "--vault" (:vault t)] args)))

(defn write-def! [t name content]
  (spit (str (:dir t) "/" name) content :encoding "UTF-8"))

(defn gen-with-def [t name content & [change project]]
  (write-def! t name content)
  (run ["generate" (or change "c") "--project" (or project "proj")
        "--root" (:root t) "--def" (str (:dir t) "/" name) "--vault" (:vault t)]))

(def six-tasks
  ["1.1 Create the \"module\" scaffold"
   "1.2 Install deps: café & 日本語"
   "1.3 Use backslash \\ as separator"
   "2.1 Implement the engine"
   "2.2 Wire \"events\" end-to-end"
   "2.3 Ship the release"])

(defn def-body [change stories]
  (str "change: " change "\nstories:\n"
       (str/join "\n"
                 (map (fn [s]
                        (str "  - id: " (:id s) "\n"
                             "    title: " (:title s) "\n"
                             "    description: d\n"
                             "    acceptanceCriteria: [\"ok\"]\n"
                             (when (seq (:dependsOn s))
                               (str "    dependsOn: [" (str/join ", " (:dependsOn s)) "]\n"))
                             "    taskRefs: [" (str/join ", " (map #(str "\"" (-> % (str/replace "\\" "\\\\") (str/replace "\"" "\\\"")) "\"") (:taskRefs s))) "]\n"))
                      stories))))

;; ---------------------------------------------------------------------------
;; golden parity
;; ---------------------------------------------------------------------------

(deftest generate-golden-parity
  (testing "generate produces byte-identical stories.md and vault notes"
    (let [t (tmp-root)
          r (gen t)]
      (is (= 0 (:exit r)))
      (is (= (file->str (str golden-dir "/stories.md"))
             (file->str (str (:root t) "/stories.md"))))
      (is (= (vault-tree golden-vault) (vault-tree (:vault t)))))))

(deftest parse-tasks-happy-path
  (testing "parse-tasks plain output matches golden"
    (let [r (run ["parse-tasks" (str fixture-root "/tasks.md")])]
      (is (= 0 (:exit r)))
      (is (= (file->str (str golden-dir "/parse-tasks.txt")) (:out r)))))
  (testing "parse-tasks --json output matches golden"
    (let [r (run ["parse-tasks" (str fixture-root "/tasks.md") "--json"])]
      (is (= 0 (:exit r)))
      (is (= (file->str (str golden-dir "/parse-tasks.json")) (:out r))))))

(deftest sync-tasks-happy-path
  (testing "sync-tasks toggles only referenced tasks, bytes + stdout match"
    (let [t (tmp-root)
          r (run ["sync-tasks" "fixture-change" "scaffold"
                  "--root" (:root t) "--def" (str (:root t) "/stories.yaml")])]
      (is (= 0 (:exit r)))
      (is (= "toggled 2 task(s) for story scaffold\n" (:out r)))
      (is (= (file->str (str golden-dir "/tasks-after-sync.md"))
             (file->str (str (:root t) "/tasks.md")))))))

(deftest append-state-happy-path
  (testing "append-state creates header on first call, appends after; bytes + stdout match"
    (let [t (tmp-root)
          r1 (run ["append-state" "fixture-change" "scaffold: created scaffold"
                   "--root" (:root t)])
          r2 (run ["append-state" "fixture-change" "core: engine done"
                   "--root" (:root t)])]
      (is (= 0 (:exit r1)))
      (is (= 0 (:exit r2)))
      (is (str/includes? (:out r1) "appended to "))
      (is (str/includes? (:out r2) "appended to "))
      (is (= (file->str (str golden-dir "/story-state.md"))
             (file->str (str (:root t) "/.story-state.md")))))))

;; ---------------------------------------------------------------------------
;; Error paths (exit 1, stderr text)
;; ---------------------------------------------------------------------------

(deftest validation-errors-file-level
  (testing "missing definition file"
    (let [t (tmp-root)
          r (run ["generate" "c" "--project" "proj" "--root" (:root t)
                  "--def" (str (:dir t) "/nope.yaml") "--vault" (:vault t)])]
      (is (= 1 (:exit r)))
      (is (str/includes? (:err r) "error: story definition not found: "))))
  (testing "definition not a YAML mapping"
    (let [t (tmp-root)
          r (gen-with-def t "bad.yaml" "- not\n- a\n- mapping\n")]
      (is (= 1 (:exit r)))
      (is (str/includes? (:err r) "error: story definition must be a YAML mapping"))))
  (testing "change mismatch"
    (let [t (tmp-root)
          r (gen-with-def t "bad.yaml"
                          "change: wrong\nstories:\n  - id: a\n    title: A\n    description: d\n    acceptanceCriteria: [\"ok\"]\n    taskRefs: []\n")]
      (is (= 1 (:exit r)))
      (is (str/includes? (:err r) "error: definition change 'wrong' != requested 'c'"))))
  (testing "empty stories list"
    (let [t (tmp-root)
          r (gen-with-def t "bad.yaml" "change: c\nstories: []\n")]
      (is (= 1 (:exit r)))
      (is (str/includes? (:err r) "error: story definition has no stories")))))

(deftest validation-errors-per-story
  (let [story "  - id: %s\n    title: A\n    description: d\n    acceptanceCriteria: [\"ok\"]\n    taskRefs: []\n"]
    (testing "duplicate story ids"
      (let [t (tmp-root)
            body (str "change: c\nstories:\n" (format story "dup") (format story "dup"))
            r (gen-with-def t "bad.yaml" body)]
        (is (= 1 (:exit r)))
        (is (str/includes? (:err r) "error: duplicate story ids"))))
    (testing "non-kebab-case id"
      (let [t (tmp-root)
            r (gen-with-def t "bad.yaml" (str "change: c\nstories:\n" (format story "Bad_Id")))]
        (is (= 1 (:exit r)))
        (is (str/includes? (:err r) "error: story id 'Bad_Id' is not kebab-case"))))
    (testing "missing title"
      (let [t (tmp-root)
            r (gen-with-def t "bad.yaml"
                            (str "change: c\nstories:\n"
                                 "  - id: a\n    description: d\n    acceptanceCriteria: [\"ok\"]\n    taskRefs: []\n"))]
        (is (= 1 (:exit r)))
        (is (str/includes? (:err r) "error: story 'a' missing title"))))
    (testing "missing description"
      (let [t (tmp-root)
            r (gen-with-def t "bad.yaml"
                            (str "change: c\nstories:\n"
                                 "  - id: a\n    title: A\n    acceptanceCriteria: [\"ok\"]\n    taskRefs: []\n"))]
        (is (= 1 (:exit r)))
        (is (str/includes? (:err r) "error: story 'a' missing description"))))
    (testing "acceptance criteria count outside 1-3"
      (let [t (tmp-root)
            r (gen-with-def t "bad.yaml"
                            (str "change: c\nstories:\n"
                                 "  - id: a\n    title: A\n    description: d\n    acceptanceCriteria: []\n    taskRefs: []\n"))]
        (is (= 1 (:exit r)))
        (is (str/includes? (:err r) "error: story 'a' must have 1-3 acceptance criteria"))))))

(deftest validation-errors-graph-level
  (let [task-1 "1.1 Create the \\\"module\\\" scaffold"
        task-2 "1.2 Install deps: café & 日本語"]
    (testing "unknown dependsOn"
      (let [t (tmp-root)
            r (gen-with-def t "bad.yaml"
                            (str "change: c\nstories:\n"
                                 "  - id: a\n    title: A\n    description: d\n    acceptanceCriteria: [\"ok\"]\n    taskRefs: [\"" task-1 "\"]\n"
                                 "  - id: b\n    title: B\n    description: d\n    acceptanceCriteria: [\"ok\"]\n    dependsOn: [zzz]\n    taskRefs: []\n"))]
        (is (= 1 (:exit r)))
        (is (str/includes? (:err r) "error: story 'b' depends on unknown story 'zzz'"))))
    (testing "dependency cycle"
      (let [t (tmp-root)
            r (gen-with-def t "bad.yaml"
                            (str "change: c\nstories:\n"
                                 "  - id: a\n    title: A\n    description: d\n    acceptanceCriteria: [\"ok\"]\n    dependsOn: [b]\n    taskRefs: [\"" task-1 "\", \"" task-2 "\", \"1.3 Use backslash \\\\ as separator\", \"2.1 Implement the engine\", \"2.2 Wire \\\"events\\\" end-to-end\", \"2.3 Ship the release\"]\n"
                                 "  - id: b\n    title: B\n    description: d\n    acceptanceCriteria: [\"ok\"]\n    dependsOn: [a]\n    taskRefs: []\n"))]
        (is (= 1 (:exit r)))
        (is (str/includes? (:err r) "error: dependency cycle: ['a', 'b', 'a']"))))
    (testing "taskRef not found in tasks.md"
      (let [t (tmp-root)
            r (gen-with-def t "bad.yaml"
                            (str "change: c\nstories:\n"
                                 "  - id: a\n    title: A\n    description: d\n    acceptanceCriteria: [\"ok\"]\n    taskRefs: [\"" task-1 "\", \"ghost task\"]\n"))]
        (is (= 1 (:exit r)))
        (is (str/includes? (:err r) "error: story 'a' taskRef not found in tasks.md: 'ghost task'"))))
    (testing "tasks.md absent"
      (let [t (tmp-root)
            r (run ["generate" "c" "--project" "proj" "--root" (:dir t)
                    "--def" (str (:dir t) "/bad.yaml") "--vault" (:vault t)])]
        (is (= 1 (:exit r)))
        (is (str/includes? (:err r) "error: tasks.md not found: "))))
    (testing "tasks not covered by any story"
      (let [t (tmp-root)
            r (gen-with-def t "bad.yaml"
                            (str "change: c\nstories:\n"
                                 "  - id: a\n    title: A\n    description: d\n    acceptanceCriteria: [\"ok\"]\n    taskRefs: [\"1.1 Create the \\\"module\\\" scaffold\"]\n"))]
        (is (= 1 (:exit r)))
        (is (str/includes? (:err r)
                           "error: tasks not covered by any story: ['1.2 Install deps: café & 日本語', '1.3 Use backslash \\ as separator', '2.1 Implement the engine', '2.2 Wire \"events\" end-to-end', '2.3 Ship the release']"))))
    (testing "unknown story id in sync-tasks"
      (let [t (tmp-root)
            r (run ["sync-tasks" "fixture-change" "nope"
                    "--root" (:root t) "--def" (str (:root t) "/stories.yaml")])]
        (is (= 1 (:exit r)))
        (is (str/includes? (:err r) "error: unknown story 'nope' in "))))))

;; ---------------------------------------------------------------------------
;; Parser rejection (exit 2, usage on stderr)
;; ---------------------------------------------------------------------------

(deftest parser-rejection
  (testing "no subcommand"
    (let [r (run)]
      (is (= 2 (:exit r)))
      (is (str/includes? (:err r) "the following arguments are required: command"))))
  (testing "unknown subcommand"
    (let [r (run "bogus")]
      (is (= 2 (:exit r)))
      (is (str/includes? (:err r) "invalid choice: 'bogus'"))))
  (testing "unknown flag"
    (let [r (run "generate" "foo" "--bogus")]
      (is (= 2 (:exit r)))
      (is (str/includes? (:err r) "unrecognized arguments: --bogus"))))
  (testing "missing positional"
    (let [r (run "generate")]
      (is (= 2 (:exit r)))
      (is (str/includes? (:err r) "the following arguments are required: change"))))
  (testing "surplus positional"
    (let [r (run "append-state" "a" "b" "c")]
      (is (= 2 (:exit r)))
      (is (str/includes? (:err r) "unrecognized arguments: c"))))
  (testing "flag on wrong subcommand"
    (let [r (run "parse-tasks" "foo" "--root" "x")]
      (is (= 2 (:exit r)))
      (is (str/includes? (:err r) "unrecognized arguments: --root"))))
  (testing "value flag without value"
    (let [r (run "generate" "foo" "--root")]
      (is (= 2 (:exit r)))
      (is (str/includes? (:err r) "argument --root: expected one argument")))))

(deftest project-flag-parsing
  (testing "generate without --project exits 2 with required-flag message"
    (let [r (run "generate" "foo" "--root" "/tmp/x")]
      (is (= 2 (:exit r)))
      (is (str/includes? (:err r) "the following arguments are required: --project"))))
  (testing "--project with no value exits 2 with expected-one-argument"
    (let [r (run "generate" "foo" "--project")]
      (is (= 2 (:exit r)))
      (is (str/includes? (:err r) "argument --project: expected one argument"))))
  (testing "--project followed by another flag exits 2 with expected-one-argument"
    (let [r (run "generate" "foo" "--project" "--root" "/tmp/x")]
      (is (= 2 (:exit r)))
      (is (str/includes? (:err r) "argument --project: expected one argument"))))
  (testing "--project rejected on parse-tasks"
    (let [r (run "parse-tasks" "foo" "--project" "p")]
      (is (= 2 (:exit r)))
      (is (str/includes? (:err r) "unrecognized arguments: --project"))))
  (testing "--project rejected on sync-tasks"
    (let [r (run "sync-tasks" "c" "s" "--project" "p")]
      (is (= 2 (:exit r)))
      (is (str/includes? (:err r) "unrecognized arguments: --project"))))
  (testing "--project rejected on append-state"
    (let [r (run "append-state" "c" "t" "--project" "p")]
      (is (= 2 (:exit r)))
      (is (str/includes? (:err r) "unrecognized arguments: --project"))))
  (testing "next without --project exits 2"
    (let [r (run "next" "foo")]
      (is (= 2 (:exit r)))
      (is (str/includes? (:err r) "the following arguments are required: --project"))))
  (testing "set-status without --project exits 2"
    (let [r (run "set-status" "foo" "a" "done")]
      (is (= 2 (:exit r)))
      (is (str/includes? (:err r) "the following arguments are required: --project")))))

(deftest help-behavior
  (testing "root help exits 0 and prints to stdout"
    (let [r (run "-h")]
      (is (= 0 (:exit r)))
      (is (str/includes? (:out r) "Helper script for the story-driven apply workflow")))
    (let [r (run "--help")]
      (is (= 0 (:exit r)))))
  (testing "subcommand help exits 0 and prints to stdout"
    (doseq [sub ["parse-tasks" "generate" "next" "set-status" "classify" "sync-tasks" "append-state"]]
      (let [r (run sub "-h")]
        (is (= 0 (:exit r)))
        (is (str/includes? (:out r) (str "usage: story_driver.clj " sub)))
        (is (= "" (:err r)))))))

;; ---------------------------------------------------------------------------
;; next
;; ---------------------------------------------------------------------------

(deftest next-runnable-progression
  (let [t (tmp-root)]
    (is (= 0 (:exit (gen t))))
    (testing "first runnable is the no-dep story, lowest id first"
      (let [r (next-json t)]
        (is (= 0 (:exit r)))
        (is (= "scaffold" (:id (:runnable (:data r)))))
        (is (= {:total 2 :done 0 :inProgress 0 :pending 2 :remaining 2} (:counts (:data r))))
        (is (= ["core"] (:blocked (:data r))))
        (is (= [] (:inProgressIds (:data r))))
        (is (= #{:id :title :description :acceptanceCriteria :taskRefs}
               (set (keys (:runnable (:data r))))))))
    (testing "after scaffold done, core becomes runnable"
      (run ["set-status" "fixture-change" "scaffold" "done" "--project" "fixture-project" "--vault" (:vault t)])
      (let [r (next-json t)]
        (is (= "core" (:id (:runnable (:data r)))))
        (is (= 1 (:done (:counts (:data r)))))
        (is (= [] (:blocked (:data r))))))
    (testing "all done: runnable null, remaining 0"
      (run ["set-status" "fixture-change" "core" "done" "--project" "fixture-project" "--vault" (:vault t)])
      (let [r (next-json t)]
        (is (nil? (:runnable (:data r))))
        (is (= 0 (:remaining (:counts (:data r)))))))))

(deftest next-stalled-run
  (let [t (tmp-root)
        stories [{:id "a" :title "A" :dependsOn [] :taskRefs [(nth six-tasks 0) (nth six-tasks 1)]}
                 {:id "b" :title "B" :dependsOn ["a"] :taskRefs [(nth six-tasks 2) (nth six-tasks 3)]}
                 {:id "c" :title "C" :dependsOn [] :taskRefs [(nth six-tasks 4) (nth six-tasks 5)]}]
        r (gen-with-def t "def.yaml" (def-body "c" stories) "c" "proj")]
    (is (= 0 (:exit r)) (str/join "\n" [(:err r) (:out r)]))
    (run ["set-status" "c" "b" "in_progress" "--project" "proj" "--vault" (:vault t)])
    (let [r (next-json t "c" "proj")]
      (is (= 0 (:exit r)))
      (is (= "a" (:id (:runnable (:data r)))))
      (is (= ["b"] (:inProgressIds (:data r)))))))

(deftest next-missing-folder
  (let [t (tmp-root)
        r (run ["next" "fixture-change" "--project" "fixture-project" "--vault" (:vault t)])]
    (is (= 1 (:exit r)))
    (is (str/includes? (:err r) "change folder not found: "))
    (is (str/includes? (:err r) "(run generate first)"))))

(deftest next-corrupt-status
  (let [t (tmp-root)]
    (gen t)
    (let [p (str (:vault t) "/Stories/fixture-project/fixture-change/scaffold.md")]
      (spit p (str/replace (file->str p) "\"pending\"" "\"banana\"") :encoding "UTF-8"))
    (let [r (next-json t)]
      (is (= 1 (:exit r)))
      (is (str/includes? (:err r) "corrupted status 'banana'")))))

(deftest next-identity-mismatch
  (let [t (tmp-root)]
    (gen t)
    (let [p (str (:vault t) "/Stories/fixture-project/fixture-change/_change.md")]
      (spit p (str/replace (file->str p) "fixture-change" "other-change") :encoding "UTF-8"))
    (let [r (next-json t)]
      (is (= 1 (:exit r)))
      (is (str/includes? (:err r) "change slug collision")))))

;; ---------------------------------------------------------------------------
;; set-status
;; ---------------------------------------------------------------------------

(deftest set-status-happy-path
  (let [t (tmp-root)]
    (gen t)
    (let [p (str (:vault t) "/Stories/fixture-project/fixture-change/scaffold.md")
          before (file->str p)
          r (run ["set-status" "fixture-change" "scaffold" "done"
                  "--project" "fixture-project" "--vault" (:vault t)])
          after (file->str p)]
      (is (= 0 (:exit r)))
      (is (= "set status done for scaffold\n" (:out r)))
      (is (str/includes? after "status: \"done\""))
      (is (= (body-of before) (body-of after))))))

(deftest set-status-invalid-status
  (let [t (tmp-root)]
    (gen t)
    (let [p (str (:vault t) "/Stories/fixture-project/fixture-change/scaffold.md")
          before (file->str p)
          r (run ["set-status" "fixture-change" "scaffold" "blocked"
                  "--project" "fixture-project" "--vault" (:vault t)])]
      (is (= 1 (:exit r)))
      (is (str/includes? (:err r) "invalid status 'blocked'"))
      (is (= before (file->str p))))))

(deftest set-status-missing-note
  (let [t (tmp-root)]
    (gen t)
    (let [r (run ["set-status" "fixture-change" "nope" "done"
                  "--project" "fixture-project" "--vault" (:vault t)])]
      (is (= 1 (:exit r)))
      (is (str/includes? (:err r) "story note not found")))))

(deftest set-status-identity-mismatch
  (let [t (tmp-root)]
    (gen t)
    (let [p (str (:vault t) "/Stories/fixture-project/fixture-change/scaffold.md")]
      (spit p (str/replace (file->str p) "change: \"fixture-change\"" "change: \"other-change\"")
            :encoding "UTF-8"))
    (let [r (run ["set-status" "fixture-change" "scaffold" "done"
                  "--project" "fixture-project" "--vault" (:vault t)])]
      (is (= 1 (:exit r)))
      (is (str/includes? (:err r) "story note identity mismatch")))))

;; ---------------------------------------------------------------------------
;; classify
;; ---------------------------------------------------------------------------

(deftest classify-creates-note
  (let [t (tmp-root)
        r (classify t "--type" "tooling" "--tech-stack" "clojure,babashka" "--repo-url" "https://x/y")]
    (is (= 0 (:exit r)))
    (let [c (file->str (str (:vault t) "/Projects/fixture-project.md"))]
      (is (str/includes? c "name: \"fixture-project\""))
      (is (str/includes? c "type: \"tooling\""))
      (is (str/includes? c "techStack: [\"clojure\",\"babashka\"]"))
      (is (str/includes? c "repoUrl: \"https://x/y\"")))))

(deftest classify-full-overwrite-and-clear
  (let [t (tmp-root)]
    (classify t "--type" "tooling" "--tech-stack" "clojure" "--repo-url" "https://x/y")
    (classify t "--type" "agent")
    (let [c (file->str (str (:vault t) "/Projects/fixture-project.md"))]
      (is (str/includes? c "type: \"agent\""))
      (is (str/includes? c "techStack: null"))
      (is (str/includes? c "repoUrl: null")))))

(deftest classify-preserves-other-keys-and-body
  (let [t (tmp-root)]
    (classify t "--type" "tooling")
    (let [p (str (:vault t) "/Projects/fixture-project.md")]
      (spit p (str "---\nname: \"fixture-project\"\ntype: \"tooling\"\ntechStack: null\nrepoUrl: null\nfoo: \"bar\"\n---\n# fixture-project\n\nCustom body 東京.\n")
            :encoding "UTF-8")
      (classify t "--type" "agent")
      (let [c (file->str p)]
        (is (str/includes? c "foo: \"bar\""))
        (is (= "# fixture-project\n\nCustom body 東京.\n" (body-of c)))))))

(deftest classify-collision-guard
  (let [t (tmp-root)
        p (str (:vault t) "/Projects/fixture-project.md")]
    (fs/create-dirs (str (:vault t) "/Projects"))
    (spit p "---\nname: \"other-project\"\n---\n# other-project\n" :encoding "UTF-8")
    (let [r (classify t "--type" "tooling")]
      (is (= 1 (:exit r)))
      (is (str/includes? (:err r) "project slug collision")))))

(deftest classify-missing-vault-root
  (let [r (run ["classify" "p" "--type" "t" "--vault" "/nonexistent-vault-xyz"])]
    (is (= 1 (:exit r)))
    (is (str/includes? (:err r) "vault root not found"))))

;; ---------------------------------------------------------------------------
;; generate regressions
;; ---------------------------------------------------------------------------

(deftest generate-status-preservation
  (let [t (tmp-root)]
    (gen t)
    (run ["set-status" "fixture-change" "core" "done" "--project" "fixture-project" "--vault" (:vault t)])
    (is (= 0 (:exit (gen t))))
    (is (str/includes? (file->str (str (:vault t) "/Stories/fixture-project/fixture-change/core.md"))
                       "status: \"done\""))))

(deftest generate-invalid-preserved-status
  (let [t (tmp-root)]
    (gen t)
    (let [p (str (:vault t) "/Stories/fixture-project/fixture-change/scaffold.md")]
      (spit p (str/replace (file->str p) "\"pending\"" "\"banana\"") :encoding "UTF-8"))
    (let [r (gen t)]
      (is (= 1 (:exit r)))
      (is (str/includes? (:err r) "corrupted status 'banana'")))))

(deftest generate-missing-vault-root
  (let [t (tmp-root)
        r (run ["generate" "fixture-change" "--project" "fixture-project"
                "--root" (:root t) "--def" (str (:root t) "/stories.yaml")
                "--vault" (str (:dir t) "/nonexistent")])]
    (is (= 1 (:exit r)))
    (is (str/includes? (:err r) "vault root not found"))))

(deftest generate-changes-merge
  (let [t (tmp-root)]
    (gen t)
    (let [stories [{:id "x" :title "X" :dependsOn [] :taskRefs six-tasks}]
          r (gen-with-def t "def2.yaml" (def-body "other-change" stories)
                          "other-change" "fixture-project")]
      (is (= 0 (:exit r)))
      (let [c (file->str (str (:vault t) "/Projects/fixture-project.md"))]
        (is (str/includes? c "[[Stories/fixture-project/fixture-change/_change|fixture-change]]"))
        (is (str/includes? c "[[Stories/fixture-project/other-change/_change|other-change]]"))))))

(deftest generate-project-collision
  (let [t (tmp-root)
        p (str (:vault t) "/Projects/fixture-project.md")]
    (fs/create-dirs (str (:vault t) "/Projects"))
    (spit p "---\nname: \"other-project\"\n---\n# other-project\n" :encoding "UTF-8")
    (let [r (gen t)]
      (is (= 1 (:exit r)))
      (is (str/includes? (:err r) "project slug collision")))))

(deftest generate-change-collision
  (let [t (tmp-root)
        p (str (:vault t) "/Stories/fixture-project/fixture-change/_change.md")]
    (fs/create-dirs (str (:vault t) "/Stories/fixture-project/fixture-change"))
    (spit p "---\nname: \"other-change\"\nproject: \"fixture-project\"\n---\n# other-change\n"
          :encoding "UTF-8")
    (let [r (gen t)]
      (is (= 1 (:exit r)))
      (is (str/includes? (:err r) "change slug collision")))))

(deftest vault-precedence-flag-over-env
  (let [t (tmp-root)]
    (fs/create-dirs (str (:dir t) "/envvault"))
    (fs/create-dirs (str (:dir t) "/flagvault"))
    (let [r (run {"OBSIDIAN_VAULT" (str (:dir t) "/envvault")}
                 ["generate" "fixture-change" "--project" "fixture-project"
                  "--root" (:root t) "--def" (str (:root t) "/stories.yaml")
                  "--vault" (str (:dir t) "/flagvault")])]
      (is (= 0 (:exit r)))
      (is (.isFile (java.io.File. (str (:dir t) "/flagvault/Projects/fixture-project.md"))))
      (is (not (.exists (java.io.File. (str (:dir t) "/envvault/Projects"))))))))

(deftest vault-precedence-default
  (let [t (tmp-root)
        home (str (:dir t) "/home")]
    (fs/create-dirs (str home "/obsidian/obsidian"))
    (let [r (run {"HOME" home}
                 ["generate" "fixture-change" "--project" "fixture-project"
                  "--root" (:root t) "--def" (str (:root t) "/stories.yaml")])]
      (is (= 0 (:exit r)))
      (is (.isFile (java.io.File. (str home "/obsidian/obsidian/Projects/fixture-project.md")))))))

(let [result (clojure.test/run-tests)]
  (System/exit (if (and (zero? (:fail result)) (zero? (:error result))) 0 1)))
