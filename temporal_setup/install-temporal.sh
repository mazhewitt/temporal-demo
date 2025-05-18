#!/bin/zsh

# Add the Temporal Helm repo (updated URL)
helm repo add temporal https://go.temporal.io/helm-charts
helm repo update

# Create a temp directory for storing PIDs
PID_DIR="/tmp/temporal_pids"
mkdir -p $PID_DIR

# Remove and recreate the namespace for Temporal
kubectl delete namespace temporal --ignore-not-found
kubectl create namespace temporal

# Uninstall any existing Temporal release and delete PVCs
helm uninstall temporaltest -n temporal || true
kubectl delete pvc --all -n temporal || true
kubectl delete jobs --all -n temporal || true

# Install Temporal server for local dev (minimal, official recommended setup)
echo "Installing Temporal with Helm..."
helm install temporaltest temporal/temporal \
  --namespace temporal \
  --set server.replicaCount=1 \
  --set cassandra.config.cluster_size=1 \
  --set elasticsearch.enabled=true \
  --set elasticsearch.replicas=1 \
  --set prometheus.enabled=false \
  --set grafana.enabled=false \
  --set alertmanager.enabled=false \
  --timeout 15m

# Wait for all Temporal components to be ready
echo "Waiting for Temporal Web UI to be ready..."
kubectl -n temporal rollout status deployment/temporaltest-web
echo "Waiting for Temporal Frontend to be ready..."
kubectl -n temporal rollout status deployment/temporaltest-frontend
echo "All deployments are ready. Current pods:"
kubectl -n temporal get pods

# Function for cleaning up port-forwards
cleanup_port_forwards() {
  echo "Cleaning up existing port forwards..."
  if [ -f "$PID_DIR/frontend_pid" ]; then
    pid=$(cat "$PID_DIR/frontend_pid")
    if ps -p $pid > /dev/null; then
      echo "Killing existing frontend port-forward (PID: $pid)"
      kill $pid 2>/dev/null || true
    fi
    rm "$PID_DIR/frontend_pid"
  fi
  
  if [ -f "$PID_DIR/web_pid" ]; then
    pid=$(cat "$PID_DIR/web_pid")
    if ps -p $pid > /dev/null; then
      echo "Killing existing web UI port-forward (PID: $pid)"
      kill $pid 2>/dev/null || true
    fi
    rm "$PID_DIR/web_pid"
  fi
  
  # Additional safety to catch any stray port-forwards
  pkill -f "kubectl -n temporal port-forward svc/temporaltest-frontend" 2>/dev/null || true
  pkill -f "kubectl -n temporal port-forward svc/temporaltest-web" 2>/dev/null || true
}

# Setup trap to clean up port-forwards on script exit
trap cleanup_port_forwards EXIT INT TERM

# Set up port forwarding
echo "Setting up port forwarding..."
cleanup_port_forwards

# Function to start port-forward with retries
start_port_forward() {
  local service=$1
  local local_port=$2
  local target_port=$3
  local pid_file=$4
  local display_name=$5
  local max_attempts=3
  local attempt=1
  
  while [ $attempt -le $max_attempts ]; do
    echo "Starting port-forward for $display_name (attempt $attempt/$max_attempts)..."
    kubectl -n temporal port-forward svc/$service $local_port:$target_port &
    local pid=$!
    echo $pid > "$PID_DIR/$pid_file"
    
    # Wait briefly to see if the port-forward establishes
    sleep 2
    if ps -p $pid > /dev/null; then
      echo "$display_name port-forward started successfully (PID: $pid)"
      return 0
    else
      echo "Failed to start $display_name port-forward, retrying..."
      rm -f "$PID_DIR/$pid_file"
      (( attempt++ ))
    fi
  done
  
  echo "ERROR: Failed to establish port-forward for $display_name after $max_attempts attempts."
  return 1
}

# Start port-forwards with retry logic
start_port_forward "temporaltest-frontend" 7233 7233 "frontend_pid" "Temporal Frontend"
frontend_status=$?

start_port_forward "temporaltest-web" 8080 8080 "web_pid" "Temporal Web UI"
web_status=$?

# Check if port-forwards were successful
if [ $frontend_status -eq 0 ] && [ $web_status -eq 0 ]; then
  echo "==================================================================="
  echo "✅ Temporal is now running with port forwarding active:"
  echo "- Temporal Frontend: localhost:7233"
  echo "- Temporal Web UI: http://localhost:8080"
  echo ""
  echo "Port forward PIDs are stored in $PID_DIR"
  echo "To stop port forwarding, you can run: kill \$(cat $PID_DIR/frontend_pid) \$(cat $PID_DIR/web_pid)"
  echo "==================================================================="
else
  echo "==================================================================="
  echo "⚠️  Warning: Some port-forwards may not have started correctly."
  echo "Please check the output above for errors."
  echo "==================================================================="
fi
