#!/bin/bash
export DOCKER_DEFAULT_PLATFORM=linux/amd64
# 定义服务名称
service=ds
# 获取当前目录
current_dir=$PWD

# 生成镜像名称，包含日期和时间戳
image_name=jcsk-harbor.tencentcloudcr.com/dataease/${service}:$(date +%F-%H%M%S)

# 构建 Docker 镜像
echo "Building Docker image: ${image_name}..."
docker build -t ${image_name} .

# 检查 Docker 构建是否成功
if [ $? -ne 0 ]; then
    echo "Error: Docker build failed."
    exit 1
fi

# 推送 Docker 镜像到镜像仓库
echo "Pushing Docker image: ${image_name}..."
docker push  ${image_name}

# 检查 Docker 推送是否成功
if [ $? -ne 0 ]; then
    echo "Error: Docker push failed."
    exit 1
fi

# 删除本地 Docker 镜像
echo "Removing local Docker image: ${image_name}..."
docker rmi ${image_name}

# # 更新 deploy.yaml 文件中的镜像引用
# echo "Updating image reference in deploy.yaml..."
# # 判断操作系统类型
# os_type=$(uname)
# if [ "$os_type" = "Darwin" ]; then
#     # macOS 系统
#     sed -i "" -E "s#(image: )(.*)#\1$image_name#" "${current_dir}/deploy.yaml"
# elif [ "$os_type" = "Linux" ]; then
#     # Linux 系统
#     sed -i -r "s#(image: )(.*)#\1$image_name#" "${current_dir}/deploy.yaml"
# else
#     echo "Unsupported operating system: $os_type"
#     exit 1
# fi

# # 检查 sed 操作是否成功
# if [ $? -ne 0 ]; then
#     echo "Error: Failed to update image reference in deploy.yaml."
#     exit 1
# fi

echo "Script execution completed successfully."

# #!/bin/bash
# service=yeecode
# current_dir=$PWD
# rm -rf dist
# cp -r ../dist/ dist
# #cd $work_dir
# #mvn clean package -Dmaven.test.skip=true
# image_name=lt-harbor.jcsk100.com/official_website/${service}:$(date +%F-%H%M%S)
# docker build -t ${image_name} .
# #docker run -it ${image_name}
# docker push ${image_name}
# docker rmi ${image_name}
# sed -i -r "s#(image: )(.*)#\1$image_name#" ${current_dir}/deploy.yaml
# #kubectl apply -f ${current_dir}/deploy.yaml
