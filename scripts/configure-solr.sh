#!/bin/bash
set -e

SOLR_CORE="documents"
SOLR_URL="http://localhost:8983/solr/$SOLR_CORE"

echo "Configuring Solr schema for vector search..."

# Add vector field type for embeddings
curl -s -X POST -H 'Content-type:application/json' \
  --data-binary '{
    "add-field-type": {
      "name": "knn_vector",
      "class": "solr.DenseVectorField",
      "vectorDimension": "384",
      "similarityFunction": "cosine"
    }
  }' \
  "$SOLR_URL/schema"

# Add document fields
curl -s -X POST -H 'Content-type:application/json' \
  --data-binary '{
    "add-field": [
      {"name": "title", "type": "text_general", "stored": true},
      {"name": "content", "type": "text_general", "stored": true},
      {"name": "file_path", "type": "string", "stored": true},
      {"name": "vector", "type": "knn_vector", "stored": true},
      {"name": "has_ocr", "type": "boolean", "stored": true},
      {"name": "ocr_confidence", "type": "pfloat", "stored": true}
    ]
  }' \
  "$SOLR_URL/schema"

echo "✓ Solr schema configured successfully"
