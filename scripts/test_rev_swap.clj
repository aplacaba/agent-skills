#!/usr/bin/env bb
;; Tests for scripts/rev_swap.clj — subprocess harness against temp fixtures.
(ns test-rev-swap
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing run-tests]]))

(def script (str (fs/canonicalize "scripts/rev_swap.clj")))

;; Strip inherited REVIEWER_SWAP_* from the environment BEFORE merging
;; fixture-specific overrides, so the real user config can never be touched.
(defn clean-env [overrides]
  (-> (into {} (System/getenv))
      (dissoc "REVIEWER_SWAP_AGENT" "REVIEWER_SWAP_CODEX_TOML")
      (merge overrides)))

(def default-agent "# fixture\nmodel: old/model\nreasoningEffort: low\n")
(def default-codex "model = \"old/cx\"\nmodel_reasoning_effort = \"low\"\nother = 1\n")

(defn make-fixture!
  "Creates a temp dir with agent.md / codex.toml fixtures (default content
  when nil) and returns {:dir :agent :codex}."
  [{:keys [agent codex]}]
  (let [dir (str (fs/create-temp-dir))
        agent-path (str dir "/agent.md")
        codex-path (str dir "/codex.toml")]
    (spit agent-path (or agent default-agent))
    (spit codex-path (or codex default-codex))
    {:dir dir :agent agent-path :codex codex-path}))

(defn run-rev-swap
  "Runs the script as a subprocess with the given env overrides and args.
  Returns {:exit :out :err}."
  [env-overrides & args]
  (let [{:keys [exit out err]}
        (p/sh (into ["bb" script] args) {:env (clean-env env-overrides) :check false})]
    {:exit exit :out out :err err}))

(defn env->agent-codex [{:keys [agent codex]}]
  (cond-> {}
    agent (assoc "REVIEWER_SWAP_AGENT" agent)
    codex (assoc "REVIEWER_SWAP_CODEX_TOML" codex)))

(defn run-on-fixture
  "Convenience: fixture + args -> subprocess result."
  [{:keys [agent codex] :as fx} & args]
  (apply run-rev-swap (env->agent-codex fx) args))

(defn switch-fixture
  "Applies a preset to a fixture and returns the result map."
  [{:keys [agent codex] :as fx} preset]
  (run-on-fixture fx preset))

;; --- sanity -----------------------------------------------------------------

(deftest smoke-help-exit-zero
  (let [{:keys [exit out]} (run-rev-swap {} "-h")]
    (is (zero? exit))
    (is (str/starts-with? out "Usage: rev-swap"))))

(deftest smoke-status-four-lines
  (let [{:keys [exit out]} (run-rev-swap {})]
    (is (zero? exit))
    (is (= 4 (count (str/split-lines out))))))

;; --- argument handling --------------------------------------------------------

(def usage-block
  (str/join "\n"
            ["Usage: rev-swap [cheap|work|hybrid]"
             "  cheap   oc: zai-coding-plan/glm-5.3-flash (max) | codex: glm-5.3-flash (max)"
             "  work    oc: openai/gpt-5.6-sol (high) | codex: gpt-5.6-sol (high)"
             "  hybrid  oc: zai-coding-plan/glm-5.3 (high) | codex: glm-5.3 (high)"]))

(deftest help-prints-verbatim-usage
  (doseq [flag ["-h" "--help"]]
    (let [{:keys [exit out]} (run-rev-swap {} flag)]
      (testing flag
        (is (zero? exit))
        (is (= usage-block (str/trim out)))))))

(deftest unknown-preset-errors-on-stderr
  (let [{:keys [exit out err]} (run-rev-swap {} "turbo")]
    (is (= 1 exit))
    (is (str/blank? out))
    (is (str/includes? err "error: unknown preset: turbo"))
    (is (str/includes? err "Usage: rev-swap [cheap|work|hybrid]"))))

(deftest surplus-arguments-ignored
  (let [fx-a (make-fixture! nil)
        fx-b (make-fixture! nil)
        bare (run-on-fixture fx-a "cheap")
        surplus (run-on-fixture fx-b "cheap" "extra")]
    (is (= (:out bare) (:out surplus)))
    (is (= (:exit bare) (:exit surplus) 0))
    (is (= (slurp (:agent fx-a)) (slurp (:agent fx-b))))
    (is (= (slurp (:codex fx-a)) (slurp (:codex fx-b))))))

;; --- path resolution and symlinks ---------------------------------------------

(defn home-fixture! []
  (let [dir (str (fs/create-temp-dir))
        agent (str dir "/.config/opencode/agent/openspec-reviewer.md")
        codex (str dir "/.codex/config.toml")]
    (fs/create-dirs (fs/parent agent))
    (fs/create-dirs (fs/parent codex))
    (spit agent default-agent)
    (spit codex default-codex)
    {:dir dir :agent agent :codex codex}))

(deftest defaults-derived-from-home-env
  (let [home (home-fixture!)
        {:keys [exit out]} (run-rev-swap {"HOME" (:dir home)} "cheap")]
    (is (zero? exit))
    (is (str/includes? out "cheap -> oc: zai-coding-plan/glm-5.3-flash"))
    (is (str/includes? (slurp (:agent home)) "model: zai-coding-plan/glm-5.3-flash"))
    (is (str/includes? (slurp (:codex home)) "model = \"glm-5.3-flash\""))))

(deftest non-empty-overrides-take-precedence
  (let [home (home-fixture!)
        fx (make-fixture! nil)
        {:keys [exit]} (run-rev-swap {"HOME" (:dir home)
                                      "REVIEWER_SWAP_AGENT" (:agent fx)
                                      "REVIEWER_SWAP_CODEX_TOML" (:codex fx)}
                                     "cheap")]
    (is (zero? exit))
    (is (str/includes? (slurp (:agent fx)) "zai-coding-plan/glm-5.3-flash"))
    (is (str/includes? (slurp (:codex fx)) "glm-5.3-flash"))
    (is (str/includes? (slurp (:agent home)) "old/model"))
    (is (str/includes? (slurp (:codex home)) "old/cx"))))

(deftest empty-override-falls-back-to-default
  (doseq [empty-env [{"REVIEWER_SWAP_AGENT" ""}
                     {"REVIEWER_SWAP_CODEX_TOML" ""}]]
    (let [home (home-fixture!)
          {:keys [exit]} (run-rev-swap (merge {"HOME" (:dir home)} empty-env) "work")]
      (testing (keys empty-env)
        (is (zero? exit))
        (is (str/includes? (slurp (:agent home)) "openai/gpt-5.6-sol"))
        (is (str/includes? (slurp (:codex home)) "gpt-5.6-sol"))))))

(deftest symlink-resolves-to-real-file
  (let [fx (make-fixture! nil)
        link (str (:dir fx) "/agent-link.md")]
    (fs/create-sym-link link (:agent fx))
    (let [{:keys [exit out]} (run-on-fixture {:agent link :codex (:codex fx)} "cheap")]
      (is (zero? exit))
      (is (str/includes? out "cheap -> oc:"))
      (is (fs/sym-link? link))
      (is (= (:agent fx) (str (fs/real-path link))))
      (is (str/includes? (slurp (:agent fx)) "zai-coding-plan/glm-5.3-flash"))
      (is (fs/regular-file? (str (:agent fx) ".rev-swap.bak")))
      (is (not (fs/exists? (str link ".rev-swap.bak")))))))

;; --- preset switching ---------------------------------------------------------

(def preset-expectations
  {:cheap  {:agent ["model: zai-coding-plan/glm-5.3-flash" "reasoningEffort: max"]
            :codex ["model = \"glm-5.3-flash\"" "model_reasoning_effort = \"max\""]
            :line "cheap -> oc: zai-coding-plan/glm-5.3-flash (max) | codex: glm-5.3-flash (max)"}
   :work   {:agent ["model: openai/gpt-5.6-sol" "reasoningEffort: high"]
            :codex ["model = \"gpt-5.6-sol\"" "model_reasoning_effort = \"high\""]
            :line "work -> oc: openai/gpt-5.6-sol (high) | codex: gpt-5.6-sol (high)"}
   :hybrid {:agent ["model: zai-coding-plan/glm-5.3" "reasoningEffort: high"]
            :codex ["model = \"glm-5.3\"" "model_reasoning_effort = \"high\""]
            :line "hybrid -> oc: zai-coding-plan/glm-5.3 (high) | codex: glm-5.3 (high)"}})

(deftest each-preset-rewrites-all-four-fields
  (doseq [[preset {:keys [agent codex line]}] preset-expectations]
    (let [fx (make-fixture! nil)
          {:keys [exit out]} (run-on-fixture fx (name preset))]
      (testing preset
        (is (zero? exit))
        (is (= line (str/trim out)))
        (doseq [expected agent] (is (str/includes? (slurp (:agent fx)) expected)))
        (doseq [expected codex] (is (str/includes? (slurp (:codex fx)) expected)))))))

(deftest replace-all-rewrites-every-match
  (let [fx (make-fixture!
            {:agent "model: a\nmodel: b\nreasoningEffort: c\nreasoningEffort: d\n"
             :codex "model = \"a\"\nmodel = \"b\"\nmodel_reasoning_effort = \"c\"\nmodel_reasoning_effort = \"d\"\n"})
        _ (run-on-fixture fx "hybrid")
        agent (slurp (:agent fx))
        codex (slurp (:codex fx))]
    (is (= 2 (count (re-seq #"model: zai-coding-plan/glm-5\.3" agent))))
    (is (= 2 (count (re-seq #"reasoningEffort: high" agent))))
    (is (= 2 (count (re-seq #"model = \"glm-5\.3\"" codex))))
    (is (= 2 (count (re-seq #"model_reasoning_effort = \"high\"" codex))))
    (is (not (str/includes? agent "model: a")))
    (is (not (str/includes? codex "model = \"b\"")))))

(deftest reasoning-effort-append-rules
  (let [missing (make-fixture! {:agent "model: keep-me\nnote line\n" :codex nil})
        _ (run-on-fixture missing "cheap")
        present (make-fixture! {:agent "model: keep-me\nreasoningEffort: original\n" :codex nil})
        _ (run-on-fixture present "cheap")
        a (slurp (:agent missing))
        b (slurp (:agent present))]
    (is (= ["model: zai-coding-plan/glm-5.3-flash" "reasoningEffort: max" "note line"]
           (str/split-lines a)))
    (is (= ["model: zai-coding-plan/glm-5.3-flash" "reasoningEffort: max"]
           (str/split-lines b)))))

(deftest near-misses-preserved-exact-prefix-rewritten
  (let [fx (make-fixture! {:agent "model:nospace\nmodels: x\nmodel: body-anchored\n"
                           :codex "  model = \"indented\"\nmodel_note = 1\n"})
        _ (run-on-fixture fx "cheap")
        agent (str/split-lines (slurp (:agent fx)))
        codex (slurp (:codex fx))]
    (is (some #{"model:nospace"} agent))
    (is (some #{"models: x"} agent))
    (is (some #{"model: zai-coding-plan/glm-5.3-flash"} agent))
    (is (str/includes? codex "  model = \"indented\""))
    (is (str/includes? codex "model_note = 1"))))

(deftest malformed-codex-line-rewritten-quoted
  (let [fx (make-fixture! {:codex "model = high\nmodel_reasoning_effort = low\n"})
        _ (run-on-fixture fx "hybrid")
        codex (slurp (:codex fx))]
    (is (str/includes? codex "model = \"glm-5.3\""))
    (is (str/includes? codex "model_reasoning_effort = \"high\""))))

;; --- status edges: non-regular / unreadable targets ----------------------------

(defn running-as-root? []
  (= "root" (System/getProperty "user.name")))

(deftest status-directory-target-is-unset
  (let [fx (make-fixture! nil)
        dir-target (str (:dir fx) "/dir.toml")]
    (fs/create-dirs dir-target)
    (let [{:keys [exit out err]} (run-on-fixture {:agent (:agent fx) :codex dir-target})]
      (is (zero? exit))
      (is (str/blank? err))
      (is (str/includes? out "opencode model: old/model"))
      (is (str/includes? out "codex model: (unset)"))
      (is (str/includes? out "codex model_reasoning_effort: (unset)"))
      (is (not (fs/exists? (str (:agent fx) ".rev-swap.bak")))))))

(deftest status-unreadable-target-is-unset
  (if (running-as-root?)
    ;; root reads through chmod 000; assert the guarded path via a directory
    ;; target instead (same catch path).
    (let [fx (make-fixture! nil)]
      (fs/create-dirs (str (:dir fx) "/dir-agent.md"))
      (let [{:keys [exit out]} (run-on-fixture {:agent (str (:dir fx) "/dir-agent.md")
                                                :codex (:codex fx)})]
        (is (zero? exit))
        (is (str/includes? out "opencode model: (unset)"))))
    (let [fx (make-fixture! nil)
          agent (:agent fx)]
      (fs/set-posix-file-permissions agent "---------")
      (let [{:keys [exit out err]} (run-on-fixture fx)]
        (is (zero? exit))
        (is (str/blank? err))
        (is (str/includes? out "opencode model: (unset)"))
        (is (str/includes? out "opencode reasoningEffort: (unset)"))
        (is (str/includes? out "codex model: old/cx"))
        (is (not (fs/exists? (str agent ".rev-swap.bak"))))
        (fs/set-posix-file-permissions agent "rw-r--r--")))))

;; --- status display -------------------------------------------------------------

(deftest status-exact-four-lines-with-values
  (let [fx (make-fixture! nil)
        {:keys [exit out]} (run-on-fixture fx)]
    (is (zero? exit))
    (is (= ["opencode model: old/model"
            "opencode reasoningEffort: low"
            "codex model: old/cx"
            "codex model_reasoning_effort: low"]
           (str/split-lines out)))))

(deftest status-first-match-wins
  (let [fx (make-fixture! {:agent "model: first/model\nmodel: second/model\n"
                           :codex "model = high\nmodel = \"valid/later\"\n"})
        {:keys [out]} (run-on-fixture fx)]
    (is (str/includes? out "opencode model: first/model"))
    (is (str/includes? out "codex model: (unset)"))))

(deftest status-empty-values-unset
  (let [fx (make-fixture! {:agent "model: \nreasoningEffort: low\n"
                           :codex "model = \"\"\nmodel_reasoning_effort = \"low\"\n"})
        {:keys [out]} (run-on-fixture fx)]
    (is (str/includes? out "opencode model: (unset)"))
    (is (str/includes? out "codex model: (unset)"))
    (is (str/includes? out "opencode reasoningEffort: low"))
    (is (str/includes? out "codex model_reasoning_effort: low"))))

(deftest status-per-target-independence
  (testing "codex missing"
    (let [fx (make-fixture! nil)
          _ (fs/delete (:codex fx))
          {:keys [exit out]} (run-on-fixture fx)]
      (is (zero? exit))
      (is (str/includes? out "codex model: (unset)"))
      (is (str/includes? out "opencode model: old/model"))))
  (testing "agent missing"
    (let [fx (make-fixture! nil)
          _ (fs/delete (:agent fx))
          {:keys [exit out]} (run-on-fixture fx)]
      (is (zero? exit))
      (is (str/includes? out "opencode model: (unset)"))
      (is (str/includes? out "codex model: old/cx"))))
  (testing "both missing"
    (let [fx (make-fixture! nil)
          _ (fs/delete (:agent fx))
          _ (fs/delete (:codex fx))
          {:keys [exit out]} (run-on-fixture fx)]
      (is (zero? exit))
      (is (every? #(str/includes? % "(unset)") (str/split-lines out)))
      (is (= 4 (count (str/split-lines out))))
      (is (not (fs/exists? (str (:dir fx) "/agent.md"))))
      (is (not (fs/exists? (str (:dir fx) "/codex.toml")))))))

;; --- validation and rolling backups --------------------------------------------

(deftest missing-target-blocks-before-mutation
  (let [fx (make-fixture! nil)
        _ (fs/delete (:codex fx))
        agent-before (slurp (:agent fx))
        {:keys [exit out err]} (run-on-fixture fx "cheap")]
    (is (= 1 exit))
    (is (str/blank? out))
    (is (= (str "error: file not found: " (:codex fx)) (str/trim err)))
    (is (= agent-before (slurp (:agent fx))))
    (is (not (fs/exists? (str (:agent fx) ".rev-swap.bak"))))))

(deftest non-regular-target-blocks-edit
  (let [fx (make-fixture! nil)
        dir-target (str (:dir fx) "/codex-dir.toml")]
    (fs/create-dirs dir-target)
    (let [{:keys [exit err]} (run-on-fixture {:agent (:agent fx) :codex dir-target} "cheap")]
      (is (= 1 exit))
      (is (= (str "error: file not found: " dir-target) (str/trim err))))))

(deftest agent-validated-before-codex
  (let [fx (make-fixture! nil)
        _ (fs/delete (:agent fx))
        _ (fs/delete (:codex fx))
        {:keys [exit err]} (run-on-fixture fx "cheap")]
    (is (= 1 exit))
    (is (str/starts-with? err "error: file not found: ")
        (is (str/includes? err "agent.md")))))

(deftest rolling-backup-content
  (let [fx (make-fixture! {:agent "model: v1\nreasoningEffort: low\n"
                           :codex "model = \"v1\"\nmodel_reasoning_effort = \"low\"\n"})]
    (run-on-fixture fx "cheap")
    (is (= "model: v1\nreasoningEffort: low\n" (slurp (str (:agent fx) ".rev-swap.bak"))))
    (is (= "model = \"v1\"\nmodel_reasoning_effort = \"low\"\n"
           (slurp (str (:codex fx) ".rev-swap.bak"))))
    (run-on-fixture fx "work")
    (is (= "model: zai-coding-plan/glm-5.3-flash\nreasoningEffort: max\n"
           (slurp (str (:agent fx) ".rev-swap.bak"))))
    (is (= "model = \"glm-5.3-flash\"\nmodel_reasoning_effort = \"max\"\n"
           (slurp (str (:codex fx) ".rev-swap.bak"))))))

(defn -main [& _]
  (let [{:keys [error fail]} (run-tests)]
    (System/exit (if (pos? (+ error fail)) 1 0))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
