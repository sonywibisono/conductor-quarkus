import React, { useState } from "react";
import { Box } from "@mui/material";
import { ArrowClockwise as SyncIcon } from "@phosphor-icons/react";
import { Button, Heading } from "components";
import { WorkflowDef } from "types/WorkflowDef";
import { useAuth } from "components/features/auth";
import TaskSyncDialog from "./dialog/TaskSyncDialog";

const actionBarStyles = {
  actionBar: {
    display: "flex",
    justifyContent: "space-between",
    alignItems: "center",
    padding: "8px 16px",
    backgroundColor: "rgba(25, 118, 210, 0.08)",
    borderRadius: "4px",
    marginBottom: "12px",
  },
};

export default function WorkflowBulkActionModule({
  selectedRows,
  refetchWorkflows,
  handleError,
  onSuccessMessage,
}: {
  selectedRows: WorkflowDef[];
  refetchWorkflows: () => void;
  handleError: (error: any) => void;
  onSuccessMessage: (msg: string) => void;
}) {
  const { isTrialExpired } = useAuth();
  const [showSyncDialog, setShowSyncDialog] = useState(false);

  const handleSyncSuccess = (message: string) => {
    onSuccessMessage(message);
    refetchWorkflows();
  };

  return (
    <>
      <Box style={actionBarStyles.actionBar}>
        <Heading level={0}>{selectedRows.length} Workflow(s) Selected</Heading>
        <Box display="flex" gap={1}>
          <Button
            variant="contained"
            color="primary"
            size="small"
            disabled={isTrialExpired || selectedRows.length === 0}
            onClick={() => setShowSyncDialog(true)}
            startIcon={<SyncIcon />}
          >
            Sync Tasks
          </Button>
        </Box>
      </Box>

      {showSyncDialog && (
        <TaskSyncDialog
          open={showSyncDialog}
          onClose={() => setShowSyncDialog(false)}
          onSuccess={handleSyncSuccess}
          selectedWorkflows={selectedRows}
        />
      )}
    </>
  );
}
