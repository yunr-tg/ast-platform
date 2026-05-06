#!/bin/bash
# Nacos配置上传脚本
# 用法: ./upload-config.sh [NACOS_SERVER] [NAMESPACE]
# 默认: ./upload-config.sh http://127.0.0.1:8848

NACOS_SERVER=${1:-http://127.0.0.1:8848}
NAMESPACE=${2:-public}
SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)

echo "上传Nacos配置到: ${NACOS_SERVER}"
echo "命名空间: ${NAMESPACE}"
echo ""

upload_config() {
    local DATA_ID=$1
    local GROUP=${2:-DEFAULT_GROUP}
    local FILE="${SCRIPT_DIR}/${DATA_ID}.yaml"

    if [ ! -f "$FILE" ]; then
        echo "文件不存在: ${FILE}"
        return 1
    fi

    local CONTENT=$(cat "$FILE" | base64 -w 0)

    echo "上传配置: ${DATA_ID} (group: ${GROUP})"

    curl -s -X POST "${NACOS_SERVER}/nacos/v1/cs/config" \
        -d "dataId=${DATA_ID}" \
        -d "group=${GROUP}" \
        -d "namespaceId=${NAMESPACE}" \
        --data-urlencode "content@${FILE}" \
        > /dev/null

    if [ $? -eq 0 ]; then
        echo "  ✓ ${DATA_ID} 上传成功"
    else
        echo "  ✗ ${DATA_ID} 上传失败"
    fi
}

# 上传共享配置
upload_config "ast-platform-common"
upload_config "ast-platform-datasource"
upload_config "ast-platform-redis"
upload_config "ast-platform-rocketmq"

# 上传应用专属配置
upload_config "task-gateway"
upload_config "task-scheduler"

echo ""
echo "所有配置上传完成"