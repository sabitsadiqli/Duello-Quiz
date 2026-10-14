# Content (question bank)

This repository is **public**, so the real question bank is not here — anyone could
read the correct answers. Layout:

| Path | What | Where |
| --- | --- | --- |
| `content/schema/question.schema.json` | JSON Schema for one question | this repo |
| `content/samples/` | A few example questions | this repo |
| `content/bank/` | The real bank: `questions/<category>/<id>.json`, `images/` | separate **private** repo, git-ignored here |

Format and rules: `docs/mvp-spec.md` §8. Writing guide: `.claude/agents/content-writer.md`.

## Set up the private bank (once)

1. Create a private GitHub repo, e.g. `sabitsadiqli/Duello-Quiz-content` (free).
2. Clone it into this folder:

```bash
git clone https://github.com/sabitsadiqli/Duello-Quiz-content.git content/bank
```

## Validate

Any JSON Schema 2020-12 validator works; for example (free, needs Node.js):

```bash
npx --yes -p ajv-cli@5 -p ajv-formats@3 ajv validate --spec=draft2020 -c ajv-formats -s content/schema/question.schema.json -d "content/bank/questions/**/*.json"
```

The schema checks structure (4 options, exactly 1 correct, at least 1 source).
File names, unique IDs, duplicate texts, images and reviewers are checked by the
private repo's own script, which its CI also runs on every push:

```bash
python3 content/bank/scripts/check.py
```

## Review workflow

`draft` (written by content-writer or a person) → Sabit fact-checks and sets
`"status": "reviewed"`, `"reviewedBy": "sabit"` → the importer activates it.
To remove a question, set `"status": "retired"`; never delete or reuse an ID.
