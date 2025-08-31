import os
import boto3
from botocore.client import Config

# MinIO connection
s3 = boto3.client(
    "s3",
    endpoint_url="http://localhost:9000",   # change if different
    aws_access_key_id="minioadmin",         # replace with MINIO_ACCESSKEY
    aws_secret_access_key="minioadmin",     # replace with MINIO_SECRETKEY
    config=Config(signature_version="s3v4"),
    region_name="us-east-1"                 # MinIO usually ignores this
)

bucket_name = "transactions"  # replace with your bucket name
base_dir = "data"          # local directory where files were generated

# Walk and upload
for root, dirs, files in os.walk(base_dir):
    for file in files:
        local_path = os.path.join(root, file)
        # Key = path inside bucket (keep same structure as local)
        relative_path = os.path.relpath(local_path, base_dir)
        s3_key = relative_path.replace("\\", "/")  # for Windows paths
        print(f"Uploading {local_path} -> {bucket_name}/{s3_key}")
        s3.upload_file(local_path, bucket_name, s3_key)

print("✅ Upload complete!")
