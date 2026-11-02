import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { parse } from "yaml";

const document = parse(await readFile(new URL("../../docs/api/openapi.yaml", import.meta.url), "utf8"));
assert.equal(document.openapi, "3.1.0");
for (const path of ["/health", "/parking/places", "/parking/places/{placeId}", "/parking/places/{placeId}/estimate"]) {
  assert.ok(document.paths?.[path], `Missing OpenAPI path: ${path}`);
}

function visit(value) {
  if (Array.isArray(value)) return value.forEach(visit);
  if (!value || typeof value !== "object") return;
  if (typeof value.$ref === "string" && value.$ref.startsWith("#/components/")) {
    const segments = value.$ref.slice(2).split("/");
    let target = document;
    for (const segment of segments) target = target?.[segment];
    assert.ok(target, `Unresolved OpenAPI reference: ${value.$ref}`);
  }
  Object.values(value).forEach(visit);
}

visit(document);
console.log("OpenAPI 3.1 YAML and local references: OK");
