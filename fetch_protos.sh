#!/bin/bash

# Configuration
PROTO_ROOT="shared/src/commonMain/proto"
BASE_URL="https://raw.githubusercontent.com/googleapis/googleapis/master"
# PROTOBUF_URL="https://raw.githubusercontent.com/protocolbuffers/protobuf/main/src"
PROTOBUF_URL="https://raw.githubusercontent.com/protocolbuffers/protobuf/v21.12/src"

# iOS Output Configuration
IOS_OUT_DIR="iosApp/iosApp/Generated"

# Create directories for fetching
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

echo "==> Fetching Google Cloud Protos..."
for proto in "${GOOGLE_PROTOS[@]}"; do
    echo "Downloading $proto..."
    curl -sSL "$BASE_URL/$proto" -o "$PROTO_ROOT/$proto"
done

echo "==> Fetching Protobuf Standard Protos..."
for proto in "${PROTOBUF_PROTOS[@]}"; do
    echo "Downloading $proto..."
    curl -sSL "$PROTOBUF_URL/$proto" -o "$PROTO_ROOT/$proto"
done

echo "==> Proto files successfully fetched to $PROTO_ROOT"
echo ""

# ---------------------------------------------------------
# iOS Swift Generation Phase
# ---------------------------------------------------------

echo "==> Starting iOS Swift Proto Generation..."

if ! command -v protoc &> /dev/null || ! command -v protoc-gen-swift &> /dev/null || ! command -v protoc-gen-grpc-swift-2 &> /dev/null; then
    echo "❌ ERROR: Missing required Protobuf/gRPC compilers."
    exit 1
fi

mkdir -p "$IOS_OUT_DIR"

rm -rf "${IOS_OUT_DIR:?}"/*

protoc \
  --proto_path="$PROTO_ROOT" \
  --swift_out="$IOS_OUT_DIR" \
  --grpc-swift-2_out="$IOS_OUT_DIR" \
  "$PROTO_ROOT"/google/cloud/speech/v2/*.proto \
  "$PROTO_ROOT"/google/api/*.proto \
  "$PROTO_ROOT"/google/longrunning/*.proto \
  "$PROTO_ROOT"/google/rpc/*.proto

# 2. FIXED: Scrub 'visionOS' from the generated files so Android Studio can parse them
echo "Cleaning up visionOS tags for older IDE parsers..."
find "$IOS_OUT_DIR" -name "*.swift" -exec sed -i '' 's/, visionOS [0-9.]*//g' {} +

echo "✅ SUCCESS! iOS Swift V2 files generated in $IOS_OUT_DIR"