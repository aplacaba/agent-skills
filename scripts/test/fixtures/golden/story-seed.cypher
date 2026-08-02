// Story graph seed for change
// change: "fixture-change"
// project: "fixture-project"
// Idempotent: each statement uses MERGE; safe to re-run.
MERGE (p:Project {name: "fixture-project"});
MERGE (c:Change {name: "fixture-change", project: "fixture-project"});
MATCH (p:Project {name: "fixture-project"}),
      (c:Change {name: "fixture-change", project: "fixture-project"})
MERGE (p)-[:BELONGS_TO]->(c);

MERGE (s:Story {id: "scaffold", change: "fixture-change", project: "fixture-project"})
ON CREATE SET s.title = "Scaffold the module", s.description = "Create the module scaffold.\nQuotes \"quoted\", backslash \\, unicode café 日本語.", s.acceptanceCriteria = ["Scaffold exists with \"quoted\" files", "Handles café and 日本語\non the second line"], s.taskRefs = ["1.1 Create the \"module\" scaffold", "1.2 Install deps: café & 日本語", "1.3 Use backslash \\ as separator"], s.status = "pending";
MATCH (c:Change {name: "fixture-change", project: "fixture-project"}),
      (s:Story {id: "scaffold", change: "fixture-change", project: "fixture-project"})
MERGE (c)-[:HAS_STORY]->(s);

MERGE (s:Story {id: "core", change: "fixture-change", project: "fixture-project"})
ON CREATE SET s.title = "Implement core behaviors", s.description = "Engine core with \"quotes\", backslash \\, and café 日本語\nand a newline", s.acceptanceCriteria = ["Events fire: \"done\"", "Unicode é, 東京, and \n newline"], s.taskRefs = ["2.1 Implement the engine", "2.2 Wire \"events\" end-to-end", "2.3 Ship the release"], s.status = "pending";
MATCH (c:Change {name: "fixture-change", project: "fixture-project"}),
      (s:Story {id: "core", change: "fixture-change", project: "fixture-project"})
MERGE (c)-[:HAS_STORY]->(s);

MATCH (a:Story {id: "core", change: "fixture-change", project: "fixture-project"})
MATCH (b:Story {id: "scaffold", change: "fixture-change", project: "fixture-project"})
MERGE (a)-[:DEPENDS_ON]->(b);