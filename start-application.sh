#!/bin/bash

# Start the Temporal service if it's not already running
echo "Checking if Temporal service is running..."
if ! nc -z localhost 7233 &> /dev/null; then
    echo "Temporal service is not running. Starting it..."
    
    # Check if we're using Docker or Kubernetes
    if command -v docker &> /dev/null; then
        echo "Using Docker to start Temporal"
        docker run -d --name temporal -p 7233:7233 -p 8080:8080 temporalio/auto-setup:latest
    else
        echo "Temporal is not running. Please start it manually."
        echo "See the main README for instructions on how to start Temporal."
        exit 1
    fi
else
    echo "Temporal service is already running."
fi

# Start the worker in the background
echo "Starting the Temporal worker..."
./gradlew :worker:run &
WORKER_PID=$!
echo "Worker started with PID: $WORKER_PID"

# Give the worker time to register
echo "Waiting for worker to initialize (5 seconds)..."
sleep 5

# Start the client web application in the foreground
echo "Starting the client web application..."
./gradlew :client:bootRun

# Cleanup when the client app is stopped
echo "Shutting down worker (PID: $WORKER_PID)..."
kill $WORKER_PID
