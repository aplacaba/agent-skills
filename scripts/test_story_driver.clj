#!/usr/bin/env bb
(ns test-story-driver
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

(def script (str (fs/canonicalize "scripts/story_driver.clj")))
(def fixtures-dir (str (fs/canonicalize "scripts/test/fixtures")))
(def golden-dir (str fixtures-dir "/golden"))
(def fixture-root (str fixtures-dir "/change-root"))

(defn tmp-root []
  (let [d (str (fs/create-temp-dir))]
    (fs/copy-tree fixture-root (str d "/change-root"))
    d))

(defn run [& args]
  (let [proc (apply p/process (concat [{:out :string :err :string :continue true}] ["bb" script] args))]
    @proc))

(defn file->str [path]
  (slurp path :encoding "UTF-8"))

(deftest generate-golden-parity
  (testing "generate produces byte-identical stories.md and story-seed.cypher"
    (let [d (tmp-root)
          r (run "generate" "fixture-change"
                 "--project" "fixture-project"
                 "--root" (str d "/change-root")
                 "--def" (str d "/change-root/stories.yaml"))]
      (is (= 0 (:exit r)))
      (is (= (file->str (str golden-dir "/stories.md"))
             (file->str (str d "/change-root/stories.md"))))
      (is (= (file->str (str golden-dir "/story-seed.cypher"))
             (file->str (str d "/change-root/story-seed.cypher")))))))

(deftest parse-tasks-happy-path
  (testing "parse-tasks plain output matches golden"
    (let [r (run "parse-tasks" (str fixture-root "/tasks.md"))]
      (is (= 0 (:exit r)))
      (is (= (file->str (str golden-dir "/parse-tasks.txt")) (:out r)))))
  (testing "parse-tasks --json output matches golden"
    (let [r (run "parse-tasks" (str fixture-root "/tasks.md") "--json")]
      (is (= 0 (:exit r)))
      (is (= (file->str (str golden-dir "/parse-tasks.json")) (:out r))))))

(deftest sync-tasks-happy-path
  (testing "sync-tasks toggles only referenced tasks, bytes + stdout match"
    (let [d (tmp-root)
          r (run "sync-tasks" "fixture-change" "scaffold"
                 "--root" (str d "/change-root")
                 "--def" (str d "/change-root/stories.yaml"))]
      (is (= 0 (:exit r)))
      (is (= "toggled 2 task(s) for story scaffold\n" (:out r)))
      (is (= (file->str (str golden-dir "/tasks-after-sync.md"))
             (file->str (str d "/change-root/tasks.md")))))))

(deftest append-state-happy-path
  (testing "append-state creates header on first call, appends after; bytes + stdout match"
    (let [d (tmp-root)
          r1 (run "append-state" "fixture-change" "scaffold: created scaffold"
                  "--root" (str d "/change-root"))
          r2 (run "append-state" "fixture-change" "core: engine done"
                  "--root" (str d "/change-root"))]
      (is (= 0 (:exit r1)))
      (is (= 0 (:exit r2)))
      (is (str/includes? (:out r1) "appended to "))
      (is (str/includes? (:out r2) "appended to "))
      (is (= (file->str (str golden-dir "/story-state.md"))
             (file->str (str d "/change-root/.story-state.md")))))))

;; ---------------------------------------------------------------------------
;; Error paths (exit 1, stderr text)
;; ---------------------------------------------------------------------------

(defn write-def! [d name content]
  (spit (str d "/" name) content :encoding "UTF-8"))

(defn gen-with-def [d name content]
  (write-def! d name content)
  (run "generate" "c" "--project" "proj" "--root" (str d "/change-root") "--def" (str d "/" name)))

(deftest validation-errors-file-level
  (testing "missing definition file"
    (let [d (tmp-root)
          r (run "generate" "c" "--project" "proj" "--root" (str d "/change-root")
                 "--def" (str d "/nope.yaml"))]
      (is (= 1 (:exit r)))
      (is (str/includes? (:err r) "error: story definition not found: "))))
  (testing "definition not a YAML mapping"
    (let [d (tmp-root)
          r (gen-with-def d "bad.yaml" "- not\n- a\n- mapping\n")]
      (is (= 1 (:exit r)))
      (is (str/includes? (:err r) "error: story definition must be a YAML mapping"))))
  (testing "change mismatch"
    (let [d (tmp-root)
          r (gen-with-def d "bad.yaml"
                          "change: wrong\nstories:\n  - id: a\n    title: A\n    description: d\n    acceptanceCriteria: [\"ok\"]\n    taskRefs: []\n")]
      (is (= 1 (:exit r)))
      (is (str/includes? (:err r) "error: definition change 'wrong' != requested 'c'"))))
  (testing "empty stories list"
    (let [d (tmp-root)
          r (gen-with-def d "bad.yaml" "change: c\nstories: []\n")]
      (is (= 1 (:exit r)))
      (is (str/includes? (:err r) "error: story definition has no stories")))))

(deftest validation-errors-per-story
  (let [story "  - id: %s\n    title: A\n    description: d\n    acceptanceCriteria: [\"ok\"]\n    taskRefs: []\n"]
    (testing "duplicate story ids"
      (let [d (tmp-root)
            body (str "change: c\nstories:\n" (format story "dup") (format story "dup"))
            r (gen-with-def d "bad.yaml" body)]
        (is (= 1 (:exit r)))
        (is (str/includes? (:err r) "error: duplicate story ids"))))
    (testing "non-kebab-case id"
      (let [d (tmp-root)
            r (gen-with-def d "bad.yaml" (str "change: c\nstories:\n" (format story "Bad_Id")))]
        (is (= 1 (:exit r)))
        (is (str/includes? (:err r) "error: story id 'Bad_Id' is not kebab-case"))))
    (testing "missing title"
      (let [d (tmp-root)
            r (gen-with-def d "bad.yaml"
                            (str "change: c\nstories:\n"
                                 "  - id: a\n    description: d\n    acceptanceCriteria: [\"ok\"]\n    taskRefs: []\n"))]
        (is (= 1 (:exit r)))
        (is (str/includes? (:err r) "error: story 'a' missing title"))))
    (testing "missing description"
      (let [d (tmp-root)
            r (gen-with-def d "bad.yaml"
                            (str "change: c\nstories:\n"
                                 "  - id: a\n    title: A\n    acceptanceCriteria: [\"ok\"]\n    taskRefs: []\n"))]
        (is (= 1 (:exit r)))
        (is (str/includes? (:err r) "error: story 'a' missing description"))))
    (testing "acceptance criteria count outside 1-3"
      (let [d (tmp-root)
            r (gen-with-def d "bad.yaml"
                            (str "change: c\nstories:\n"
                                 "  - id: a\n    title: A\n    description: d\n    acceptanceCriteria: []\n    taskRefs: []\n"))]
        (is (= 1 (:exit r)))
        (is (str/includes? (:err r) "error: story 'a' must have 1-3 acceptance criteria"))))))

(deftest validation-errors-graph-level
  (let [task-1 "1.1 Create the \\\"module\\\" scaffold"
        task-2 "1.2 Install deps: café & 日本語"]
    (testing "unknown dependsOn"
      (let [d (tmp-root)
            r (gen-with-def d "bad.yaml"
                            (str "change: c\nstories:\n"
                                 "  - id: a\n    title: A\n    description: d\n    acceptanceCriteria: [\"ok\"]\n    taskRefs: [\"" task-1 "\"]\n"
                                 "  - id: b\n    title: B\n    description: d\n    acceptanceCriteria: [\"ok\"]\n    dependsOn: [zzz]\n    taskRefs: []\n"))]
        (is (= 1 (:exit r)))
        (is (str/includes? (:err r) "error: story 'b' depends on unknown story 'zzz'"))))
    (testing "dependency cycle"
      (let [d (tmp-root)
            r (gen-with-def d "bad.yaml"
                            (str "change: c\nstories:\n"
                                 "  - id: a\n    title: A\n    description: d\n    acceptanceCriteria: [\"ok\"]\n    dependsOn: [b]\n    taskRefs: [\"" task-1 "\", \"" task-2 "\", \"1.3 Use backslash \\\\ as separator\", \"2.1 Implement the engine\", \"2.2 Wire \\\"events\\\" end-to-end\", \"2.3 Ship the release\"]\n"
                                 "  - id: b\n    title: B\n    description: d\n    acceptanceCriteria: [\"ok\"]\n    dependsOn: [a]\n    taskRefs: []\n"))]
        (is (= 1 (:exit r)))
        (is (str/includes? (:err r) "error: dependency cycle: ['a', 'b', 'a']"))))
    (testing "taskRef not found in tasks.md"
      (let [d (tmp-root)
            r (gen-with-def d "bad.yaml"
                            (str "change: c\nstories:\n"
                                 "  - id: a\n    title: A\n    description: d\n    acceptanceCriteria: [\"ok\"]\n    taskRefs: [\"" task-1 "\", \"ghost task\"]\n"))]
        (is (= 1 (:exit r)))
        (is (str/includes? (:err r) "error: story 'a' taskRef not found in tasks.md: 'ghost task'"))))
    (testing "tasks.md absent"
      (let [d (str (fs/create-temp-dir))
            r (run "generate" "c" "--project" "proj" "--root" d "--def" (str d "/bad.yaml"))]
        (is (= 1 (:exit r)))
        (is (str/includes? (:err r) "error: tasks.md not found: "))))
    (testing "tasks not covered by any story"
      (let [d (tmp-root)
            r (gen-with-def d "bad.yaml"
                            (str "change: c\nstories:\n"
                                 "  - id: a\n    title: A\n    description: d\n    acceptanceCriteria: [\"ok\"]\n    taskRefs: [\"1.1 Create the \\\"module\\\" scaffold\"]\n"))]
        (is (= 1 (:exit r)))
        (is (str/includes? (:err r)
                           "error: tasks not covered by any story: ['1.2 Install deps: café & 日本語', '1.3 Use backslash \\ as separator', '2.1 Implement the engine', '2.2 Wire \"events\" end-to-end', '2.3 Ship the release']"))))
    (testing "unknown story id in sync-tasks"
      (let [d (tmp-root)
            r (run "sync-tasks" "fixture-change" "nope"
                   "--root" (str d "/change-root")
                   "--def" (str d "/change-root/stories.yaml"))]
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
      (is (str/includes? (:err r) "unrecognized arguments: --project")))))

(deftest seed-shape
  (testing "emitted seed contains project-scoped statements"
    (let [d (tmp-root)
          r (run "generate" "fixture-change"
                 "--project" "fixture-project"
                 "--root" (str d "/change-root")
                 "--def" (str d "/change-root/stories.yaml"))
          seed (file->str (str d "/change-root/story-seed.cypher"))]
      (is (= 0 (:exit r)))
      (is (str/includes? seed "MERGE (p:Project {name: \"fixture-project\"})"))
      (is (str/includes? seed "MERGE (c:Change {name: \"fixture-change\", project: \"fixture-project\"})"))
      (is (str/includes? seed "MERGE (p)-[:BELONGS_TO]->(c)"))
      (is (str/includes? seed "MERGE (s:Story {id: \"scaffold\", change: \"fixture-change\", project: \"fixture-project\"})"))
      (is (str/includes? seed "MERGE (s:Story {id: \"core\", change: \"fixture-change\", project: \"fixture-project\"})"))
      (is (str/includes? seed "MATCH (c:Change {name: \"fixture-change\", project: \"fixture-project\"}),"))
      (is (str/includes? seed "MATCH (a:Story {id: \"core\", change: \"fixture-change\", project: \"fixture-project\"})"))
      (is (str/includes? seed "MATCH (b:Story {id: \"scaffold\", change: \"fixture-change\", project: \"fixture-project\"})"))
      (is (str/includes? seed "MERGE (a)-[:DEPENDS_ON]->(b)"))
      (is (str/includes? seed "// project: \"fixture-project\"")))))

(deftest help-behavior
  (testing "root help exits 0 and prints to stdout"
    (let [r (run "-h")]
      (is (= 0 (:exit r)))
      (is (str/includes? (:out r) "Helper script for the story-driven apply workflow")))
    (let [r (run "--help")]
      (is (= 0 (:exit r)))))
  (testing "subcommand help exits 0 and prints to stdout"
    (doseq [sub ["parse-tasks" "generate" "sync-tasks" "append-state"]]
      (let [r (run sub "-h")]
        (is (= 0 (:exit r)))
        (is (str/includes? (:out r) (str "usage: story_driver.clj " sub)))
        (is (= "" (:err r)))))))

(let [result (clojure.test/run-tests)]
  (System/exit (if (and (zero? (:fail result)) (zero? (:error result))) 0 1)))
