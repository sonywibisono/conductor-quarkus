import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import TaskSyncDialog from "./TaskSyncDialog";
import { WorkflowDef } from "types/WorkflowDef";

const mockMutateAsync = vi.fn().mockResolvedValue({});

vi.mock("utils/query", () => ({
  useFetch: () => ({
    data: [
      {
        name: "tarif_kvarh",
        retryCount: 3,
        timeoutSeconds: 60,
      },
    ],
    isFetching: false,
  }),
  useActionWithPath: () => ({
    mutateAsync: mockMutateAsync,
  }),
}));

vi.mock("components", () => ({
  Button: ({ children, onClick, disabled }: any) => (
    <button onClick={onClick} disabled={disabled}>
      {children}
    </button>
  ),
  Heading: ({ children }: any) => <h4>{children}</h4>,
}));

const mockWorkflows: WorkflowDef[] = [
  {
    name: "workflow_standar_rm_non_rm_i1_2200_va",
    description: "Alpha Workflow",
    version: 1,
    restartable: true,
    timeoutSeconds: 0,
    ownerEmail: "test@example.com",
    updateTime: Date.now(),
    workflowStatusListenerEnabled: false,
    failureWorkflow: "",
    schemaVersion: 2,
    tasks: [
      {
        name: "tarif_kvarh",
        taskReferenceName: "tarif_kvarh_ref_1",
        type: "INLINE" as any,
        description: "",
        inputParameters: {
          evaluatorType: "javascript",
          expression: "function generatedFunction() { return $.biaya_kvar * $.faktorq_value; } generatedFunction();",
        },
      },
    ] as any,
  },
  {
    name: "workflow_standar_rm_non_rm_s2_1300_va",
    description: "Beta Workflow",
    version: 1,
    restartable: true,
    timeoutSeconds: 0,
    ownerEmail: "test@example.com",
    updateTime: Date.now(),
    workflowStatusListenerEnabled: false,
    failureWorkflow: "",
    schemaVersion: 2,
    tasks: [
      {
        name: "tarif_kvarh",
        taskReferenceName: "tarif_kvarh_ref_1",
        type: "INLINE" as any,
        description: "",
        inputParameters: {
          evaluatorType: "javascript",
          expression: "old_formula()",
        },
      },
    ] as any,
  },
  {
    name: "workflow_tanpa_task",
    description: "Workflow Target Tanpa Task",
    version: 1,
    restartable: true,
    timeoutSeconds: 0,
    ownerEmail: "test@example.com",
    updateTime: Date.now(),
    workflowStatusListenerEnabled: false,
    failureWorkflow: "",
    schemaVersion: 2,
    tasks: [],
  },
];

describe("TaskSyncDialog", () => {
  it("renders task sync dialog with reference workflow selection and task comparison", () => {
    render(
      <TaskSyncDialog
        open={true}
        onClose={vi.fn()}
        onSuccess={vi.fn()}
        selectedWorkflows={mockWorkflows}
      />,
    );

    expect(screen.getByText("Sinkronisasi Task Berdasarkan Workflow Acuan")).toBeInTheDocument();
    expect(screen.getByText("tarif_kvarh")).toBeInTheDocument();
  });

  it("triggers task synchronization matching reference workflow expressions across target workflows", async () => {
    const handleSuccess = vi.fn();
    render(
      <TaskSyncDialog
        open={true}
        onClose={vi.fn()}
        onSuccess={handleSuccess}
        selectedWorkflows={mockWorkflows}
      />,
    );

    const syncButton = screen.getByRole("button", { name: /Samakan Task/i });
    expect(syncButton).not.toBeDisabled();

    fireEvent.click(syncButton);

    await waitFor(() => {
      expect(mockMutateAsync).toHaveBeenCalledWith(
        expect.objectContaining({
          method: "PUT",
          path: "/metadata/workflow",
        }),
      );
      expect(handleSuccess).toHaveBeenCalled();
    });
  });

  it("adds missing task from reference workflow to target workflow that lacks the task", async () => {
    const handleSuccess = vi.fn();
    render(
      <TaskSyncDialog
        open={true}
        onClose={vi.fn()}
        onSuccess={handleSuccess}
        selectedWorkflows={mockWorkflows}
      />,
    );

    const syncButton = screen.getByRole("button", { name: /Samakan Task/i });
    fireEvent.click(syncButton);

    await waitFor(() => {
      const callArg = mockMutateAsync.mock.calls[mockMutateAsync.mock.calls.length - 1][0];
      const payloadWorkflows = JSON.parse(callArg.body);
      const targetWfWithoutTask = payloadWorkflows.find((w: any) => w.name === "workflow_tanpa_task");
      expect(targetWfWithoutTask.tasks).toHaveLength(1);
      expect(targetWfWithoutTask.tasks[0].name).toBe("tarif_kvarh");
    });
  });
});
