# Temporal Workflow Example

This project demonstrates a structured product order processing workflow using [Temporal](https://temporal.io/), a microservice orchestration platform. The workflow processes a structured product order through multiple stages: validation, pricing, execution, and booking.

## Project Structure

- **[workflow-api](./workflow-api/README.md)**: Contains the API interfaces and data models for the workflow
- **[worker](./worker/README.md)**: Implements the workflow logic and activities
- **[client](./client/README.md)**: Client application to submit orders to the workflow

> **Note**: There's a duplicate directory `workflow_api` alongside `workflow-api` in the project. The build files reference `workflow-api` (with a hyphen), which is the correct version to use. For cleanup, consider removing the `workflow_api` (with underscore) directory if not needed.

## Prerequisites

- JDK 21 or higher
- Docker and Docker Compose
- Kubernetes cluster (local like Minikube, Docker Desktop, or Kind)
- Helm

## Getting Started

### 1. Install Temporal

The project includes a setup script to install Temporal in your Kubernetes cluster:

```bash
# Make the script executable if needed
chmod +x ./temporal_setup/install-temporal.sh

# Run the setup script
./temporal_setup/install-temporal.sh
```

This script will:
- Install Temporal via Helm in the `temporal` namespace
- Set up port forwarding:
  - Temporal Frontend: `localhost:7233`
  - Temporal Web UI: `http://localhost:8080`

### 2. Build the Project

Build all components with Gradle:

```bash
./gradlew build
```

### 3. Start the Worker

Start the worker to process workflow tasks:

```bash
./gradlew :worker:run
```

Keep this terminal running while testing the workflow.

### 4. Running the E2E Test

The project includes an end-to-end test that creates a workflow execution and verifies the result:

```bash
./gradlew :client:test --tests "com.example.client.OrderWorkflowE2ETest"
```

## How the Test Works

The E2E test (`OrderWorkflowE2ETest`) works as follows:

1. **Setup**: Connects to the Temporal service running on `localhost:7233`
2. **Test Execution**:
   - Creates a sample `StructuredProductOrder` with random UUID
   - Submits the order to the Temporal workflow using the `ORDER_TASK_QUEUE`
   - Waits for the workflow to complete (with a 10-second timeout)
3. **Comprehensive Verification**:
   - Verifies that the workflow returns a non-null result
   - Confirms that the result contains "Order workflow completed successfully"
   - Validates that the order was successfully booked with the correct order ID
   - Checks that important order data (client information, order ID) appears in the result
   - Handles timeouts gracefully for long-running workflows

The test has an explicit 10-second timeout annotation using JUnit's `@Timeout`. If the workflow takes longer than 8 seconds to complete, the test will handle the timeout gracefully rather than failing.

## Monitoring

You can monitor workflow executions through the Temporal Web UI at http://localhost:8080

## Troubleshooting

If you encounter issues with port forwarding or connecting to Temporal:

1. Verify that Temporal is running in your Kubernetes cluster:
   ```bash
   kubectl -n temporal get pods
   ```

2. Check port forwarding status:
   ```bash
   ps aux | grep "port-forward"
   ```

3. Restart port forwarding if needed:
   ```bash
   # Kill existing port forwards
   pkill -f "kubectl -n temporal port-forward"
   
   # Restart port forwarding
   kubectl -n temporal port-forward svc/temporaltest-frontend 7233:7233 &
   kubectl -n temporal port-forward svc/temporaltest-web 8080:8080 &
   ```

## License

This project is for demonstration purposes.
