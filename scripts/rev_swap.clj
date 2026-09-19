#!/usr/bin/env bb
;; rev-swap: switch reviewer model presets across the opencode reviewer agent
;; frontmatter and the codex config.toml.
(ns rev-swap
  (:require [babashka.fs :as fs]
            [clojure.string :as str]))

(def presets
  {:cheap  {:oc-model "zai-coding-plan/glm-5.3-flash" :oc-effort "max"
            :cx-model "glm-5.3-flash"                 :cx-effort "max"}
   :work   {:oc-model "openai/gpt-5.6-sol"           :oc-effort "high"
             :cx-model "gpt-5.6-sol"                  :cx-effort "high"}
   :hybrid {:oc-model "zai-coding-plan/glm-5.3"      :oc-effort "high"
            :cx-model "glm-5.3"                      :cx-effort "high"}})

(defn print-usage
  ([] (print-usage *out*))
  ([w]
   (binding [*out* w]
     (println "Usage: rev-swap [cheap|work|hybrid]")
     (doseq [[k p] presets]
       (println (format "  %-6s  oc: %s (%s) | codex: %s (%s)"
                        (name k)
                        (:oc-model p) (:oc-effort p)
                        (:cx-model p) (:cx-effort p)))))))

(defn- display [v]
  (if (or (nil? v) (str/blank? v)) "(unset)" (str/trim v)))

;; --- target resolution -----------------------------------------------------

(defn- env-path [var-name default]
  (let [v (System/getenv var-name)]
    (if (str/blank? v) default v)))

(defn- home-dir []
  (or (not-empty (System/getenv "HOME")) (str (fs/home))))

(defn agent-path-raw []
  (env-path "REVIEWER_SWAP_AGENT"
            (str (home-dir) "/.config/opencode/agent/openspec-reviewer.md")))

(defn codex-path-raw []
  (env-path "REVIEWER_SWAP_CODEX_TOML" (str (home-dir) "/.codex/config.toml")))

;; nil on resolution failure; callers decide error vs (unset).
(defn canonical-path [p]
  (try (str (fs/canonicalize p)) (catch Exception _ nil)))

(defn agent-path [] (or (canonical-path (agent-path-raw)) (agent-path-raw)))
(defn codex-path [] (codex-path-raw))

;; --- status reads (guarded, first-match) -----------------------------------

(defn- file-lines [file]
  (try
    (when (and file (fs/regular-file? file))
      (str/split-lines (slurp (str file))))
    (catch Exception _ nil)))

(defn- oc-value [file field]
  (let [prefix (str field ": ")]
    (when-let [line (->> (file-lines file) (filter #(str/starts-with? % prefix)) first)]
      (subs line (count prefix)))))

(defn- codex-value [file field]
  (let [prefix (str field " = ")
        pattern (re-pattern (str (java.util.regex.Pattern/quote field) " = \"([^\"]*)\""))]
    (when-let [line (->> (file-lines file) (filter #(str/starts-with? % prefix)) first)]
      (nth (re-matches pattern line) 1 nil))))

(defn print-status []
  (let [agent (agent-path) codex (codex-path)]
    (println "opencode model:" (display (oc-value agent "model")))
    (println "opencode reasoningEffort:" (display (oc-value agent "reasoningEffort")))
    (println "codex model:" (display (codex-value codex "model")))
    (println "codex model_reasoning_effort:" (display (codex-value codex "model_reasoning_effort")))))

;; --- preset editing ---------------------------------------------------------

(defn- ensure-regular! [path]
  (when-not (try (fs/regular-file? path) (catch Exception _ false))
    (binding [*out* *err*]
      (println "error: file not found:" path))
    (System/exit 1)))

(defn- backup! [path]
  (fs/copy path (str path ".rev-swap.bak") {:replace-existing true}))

(defn- rewrite-agent [lines {:keys [oc-model oc-effort]}]
  (let [rewritten (map (fn [l]
                         (cond
                           (str/starts-with? l "model: ") (str "model: " oc-model)
                           (str/starts-with? l "reasoningEffort: ") (str "reasoningEffort: " oc-effort)
                           :else l))
                       lines)]
    (if (some #(str/starts-with? % "reasoningEffort: ") rewritten)
      rewritten
      (mapcat (fn [l]
                (if (str/starts-with? l "model: ")
                  [l (str "reasoningEffort: " oc-effort)]
                  [l]))
              rewritten))))

(defn- rewrite-codex [lines {:keys [cx-model cx-effort]}]
  (map (fn [l]
         (cond
           (str/starts-with? l "model = ") (str "model = \"" cx-model "\"")
           (str/starts-with? l "model_reasoning_effort = ") (str "model_reasoning_effort = \"" cx-effort "\"")
           :else l))
       lines))

(defn- rewrite-file! [path rewriter fields]
  (let [out (rewriter (str/split-lines (slurp path)) fields)]
    (spit path (str (str/join "\n" out) "\n"))))

(defn apply-preset [preset]
  (let [p (get presets preset)
        agent (agent-path)
        codex (codex-path)]
    (ensure-regular! agent)
    (ensure-regular! codex)
    (backup! agent)
    (backup! codex)
    (rewrite-file! agent rewrite-agent (select-keys p [:oc-model :oc-effort]))
    (rewrite-file! codex rewrite-codex (select-keys p [:cx-model :cx-effort]))
    (println (format "%s -> oc: %s (%s) | codex: %s (%s)"
                     (name preset)
                     (:oc-model p) (:oc-effort p)
                     (:cx-model p) (:cx-effort p)))))

(defn -main [& args]
  (let [[cmd] args]
    (cond
      (nil? cmd) (print-status)
      (contains? #{"-h" "--help"} cmd) (print-usage)
      (contains? presets (keyword cmd)) (do (apply-preset (keyword cmd)))
      :else (do (binding [*out* *err*]
                  (println "error: unknown preset:" (or cmd ""))
                  (print-usage *err*))
                (System/exit 1)))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
