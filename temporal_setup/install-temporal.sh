#!/bin/zsh

# Add the Temporal Helm repo (updated URL)
helm repo add temporal https://go.temporal.io/helm-charts

# Update your Helm repos
helm repo update

# Create a namespace for Temporal (optional)
kubectl create namespace temporal --dry-run=client -o yaml | kubectl apply -f -

# Install the latest Temporal server
helm install temporaltest temporal/temporal --namespace temporal

# Wait for Temporal Web UI to be ready
kubectl -n temporal rollout status deployment/temporaltest-temporal-web
kubectl -n temporal get pods
