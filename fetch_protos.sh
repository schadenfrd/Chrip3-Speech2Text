#!/bin/bash

# Configuration
PROTO_ROOT="shared/src/commonMain/proto"
BASE_URL="https://raw.githubusercontent.com/googleapis/googleapis/master"
PROTOBUF_URL="https://raw.githubusercontent.com/protocolbuffers/protobuf/main/src"

# Create directories
mkdir -p "$PROTO_ROOT/google/cloud/speech/v2"
mkdir -p "$PROTO_ROOT/google/api"
mkdir -p "$PROTO_ROOT/google/longrunning"
mkdir -p "$PROTO_ROOT/google/rpc"
mkdir -p "$PROTO_ROOT/google/protobuf"

# Proto files to download from googleapis
GOOGLE_PROTOS=(
    "google/cloud/speech/v2/cloud_speech.proto"
    "google/api/annotations.proto"
    "google/api/http.proto"
    "google/api/client.proto"
    "google/api/field_behavior.proto"
    "google/api/resource.proto"
    "google/api/auth.proto"
    "google/api/launch_stage.proto"
    "google/api/field_info.proto"
    "google/longrunning/operations.proto"
    "google/rpc/status.proto"
    "google/rpc/error_details.proto"
    "google/rpc/code.proto"
)

# Proto files to download from protobuf (standard types)
PROTOBUF_PROTOS=(
    "google/protobuf/any.proto"
    "google/protobuf/duration.proto"
    "google/protobuf/empty.proto"
    "google/protobuf/field_mask.proto"
    "google/protobuf/timestamp.proto"
    "google/protobuf/descriptor.proto"
    "google/protobuf/wrappers.proto"
)

echo "Fetching Google Cloud Protos..."
for proto in "${GOOGLE_PROTOS[@]}"; do
    echo "Downloading $proto..."
    curl -sSL "$BASE_URL/$proto" -o "$PROTO_ROOT/$proto"
done

echo "Fetching Protobuf Standard Protos..."
for proto in "${PROTOBUF_PROTOS[@]}"; do
    echo "Downloading $proto..."
    curl -sSL "$PROTOBUF_URL/$proto" -o "$PROTO_ROOT/$proto"
done

echo "Done! Proto files are located in $PROTO_ROOT"
