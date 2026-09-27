import React, { useState, useMemo, useEffect } from "react";
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Box,
  Typography,
  Checkbox,
  FormControlLabel,
  Switch,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Chip,
  Alert,
  LinearProgress,
  IconButton,
  Divider,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  Accordion,
  AccordionSummary,
  AccordionDetails,
} from "@mui/material";
import {
  X as CloseIcon,
  ArrowClockwise as SyncIcon,
  CheckCircle,
  WarningCircle,
  PlusCircle,
  CaretDown,
  Code as CodeIcon,
} from "@phosphor-icons/react";
import { Button } from "components";
import { WorkflowDef } from "types/WorkflowDef";
import { CommonTaskDef } from "types/TaskType";
import { mapWalk, flatten } from "utils/workflow";
import { useActionWithPath } from "utils/query";
import { logger } from "utils/logger";

export interface TaskSyncDialogProps {
  open: boolean;
  onClose: () => void;
  onSuccess: (message: string) => void;
  selectedWorkflows: WorkflowDef[];
}

export interface TaskCompareStatus {
  name: string;
  type: string;
  referenceTask: any;
  targetCount: number;
  matchingCount: number;
  mismatchCount: number;
  notFoundCount: number;
  status: "SYNCED" | "MISMATCH" | "NOT_FOUND";
  expressionPreview?: string;
}

export default function TaskSyncDialog({
  open,
  onClose,
  onSuccess,
  selectedWorkflows,
}: TaskSyncDialogProps) {
  // Reference Workflow state (Workflow Acuan)
  const [referenceWorkflowName, setReferenceWorkflowName] = useState<string>("");
  const [selectedTaskNames, setSelectedTaskNames] = useState<Set<string>>(new Set());

  // Sync settings
  const [syncExpressionAndParams, setSyncExpressionAndParams] = useState(true);
  const [syncTaskDef, setSyncTaskDef] = useState(true);
  const [syncTimeoutRetry, setSyncTimeoutRetry] = useState(true);
  const [addMissingTasks, setAddMissingTasks] = useState(true);

  // Execution state
  const [isSyncing, setIsSyncing] = useState(false);
  const [syncError, setSyncError] = useState<string | null>(null);

  // Initialize reference workflow when dialog opens or selectedWorkflows change
  useEffect(() => {
    if (selectedWorkflows.length > 0) {
      if (
        !referenceWorkflowName ||
        !selectedWorkflows.some((w) => w.name === referenceWorkflowName)
      ) {
        setReferenceWorkflowName(selectedWorkflows[0].name);
      }
    }
  }, [selectedWorkflows, referenceWorkflowName]);

  // Extract reference workflow and target workflows
  const referenceWorkflow = useMemo(() => {
    return selectedWorkflows.find((w) => w.name === referenceWorkflowName);
  }, [selectedWorkflows, referenceWorkflowName]);

  const targetWorkflows = useMemo(() => {
    return selectedWorkflows.filter((w) => w.name !== referenceWorkflowName);
  }, [selectedWorkflows, referenceWorkflowName]);

  // Extract tasks from reference workflow and compare with target workflows
  const tasksComparison = useMemo<TaskCompareStatus[]>(() => {
    if (!referenceWorkflow || !referenceWorkflow.tasks) return [];

    // Get all tasks from reference workflow
    const refTasksList = flatten(referenceWorkflow.tasks as CommonTaskDef[]);
    const uniqueRefTasks = new Map<string, any>();

    refTasksList.forEach((t: any) => {
      if (t && t.name && !uniqueRefTasks.has(t.name)) {
        uniqueRefTasks.set(t.name, t);
      }
    });

    const results: TaskCompareStatus[] = [];

    uniqueRefTasks.forEach((refTask: any, taskName: string) => {
      let targetCount = 0;
      let matchingCount = 0;
      let mismatchCount = 0;
      let notFoundCount = 0;

      const refParamsStr = JSON.stringify(refTask.inputParameters || {});
      const refTaskDefStr = JSON.stringify(refTask.taskDefinition || {});

      targetWorkflows.forEach((targetWf) => {
        if (!targetWf.tasks) return;

        const targetTasksList = flatten(targetWf.tasks as CommonTaskDef[]);
        const matchingTargetTask: any = targetTasksList.find((t: any) => t && t.name === taskName);

        if (matchingTargetTask) {
          targetCount++;
          const targetParamsStr = JSON.stringify(matchingTargetTask.inputParameters || {});
          const targetTaskDefStr = JSON.stringify(matchingTargetTask.taskDefinition || {});

          if (refParamsStr === targetParamsStr && refTaskDefStr === targetTaskDefStr) {
            matchingCount++;
          } else {
            mismatchCount++;
          }
        } else {
          notFoundCount++;
        }
      });

      let status: "SYNCED" | "MISMATCH" | "NOT_FOUND" = "SYNCED";
      if (targetCount === 0) {
        status = "NOT_FOUND";
      } else if (mismatchCount > 0) {
        status = "MISMATCH";
      }

      // Expression preview for INLINE or LAMBDA tasks
      let expressionPreview: string | undefined = undefined;
      if (refTask.inputParameters && typeof refTask.inputParameters === "object") {
        if ("expression" in refTask.inputParameters) {
          expressionPreview = String(refTask.inputParameters.expression);
        } else if ("scriptExpression" in refTask.inputParameters) {
          expressionPreview = String(refTask.inputParameters.scriptExpression);
        }
      }

      results.push({
        name: taskName,
        type: refTask.type || "SIMPLE",
        referenceTask: refTask,
        targetCount,
        matchingCount,
        mismatchCount,
        notFoundCount,
        status,
        expressionPreview,
      });
    });

    return results.sort((a, b) => a.name.localeCompare(b.name));
  }, [referenceWorkflow, targetWorkflows]);

  // Auto-select tasks when reference workflow changes
  useEffect(() => {
    if (tasksComparison.length > 0) {
      const initialSelected = new Set<string>();
      tasksComparison.forEach((task) => {
        initialSelected.add(task.name);
      });
      setSelectedTaskNames(initialSelected);
    }
  }, [tasksComparison]);

  const handleToggleTask = (taskName: string) => {
    setSelectedTaskNames((prev) => {
      const next = new Set(prev);
      if (next.has(taskName)) {
        next.delete(taskName);
      } else {
        next.add(taskName);
      }
      return next;
    });
  };

  const handleSelectAll = (checked: boolean) => {
    if (checked) {
      const allNames = tasksComparison.map((t) => t.name);
      setSelectedTaskNames(new Set(allNames));
    } else {
      setSelectedTaskNames(new Set());
    }
  };

  const handleSelectMismatchedOnly = () => {
    const mismatchedNames = tasksComparison
      .filter((t) => t.status === "MISMATCH" || t.status === "NOT_FOUND")
      .map((t) => t.name);
    setSelectedTaskNames(new Set(mismatchedNames));
  };

  const updateWorkflowsAction = useActionWithPath();

  const handleExecuteSync = async () => {
    if (selectedTaskNames.size === 0 || !referenceWorkflow) return;
    setIsSyncing(true);
    setSyncError(null);

    try {
      // Map of reference tasks for selected task names
      const refTaskMap = new Map<string, any>();
      tasksComparison.forEach((tc) => {
        if (selectedTaskNames.has(tc.name)) {
          refTaskMap.set(tc.name, tc.referenceTask);
        }
      });

      // Update each target workflow to match reference tasks
      const updatedTargetWorkflows = targetWorkflows.map((targetWf) => {
        let updatedTasks = targetWf.tasks ? [...(targetWf.tasks as CommonTaskDef[])] : [];

        // 1. Update existing tasks using mapWalk
        updatedTasks = mapWalk(updatedTasks, (targetTask) => {
          if (!targetTask || !targetTask.name || !refTaskMap.has(targetTask.name)) {
            return targetTask;
          }

          const refTask = refTaskMap.get(targetTask.name)!;
          const updatedTask: any = { ...targetTask };

          // Sync Expressions and inputParameters (formula, evaluatorType, script, etc.)
          if (syncExpressionAndParams && refTask.inputParameters) {
            updatedTask.inputParameters = {
              ...refTask.inputParameters,
            };
          }

          // Sync Embedded Task Definition
          if (syncTaskDef && refTask.taskDefinition) {
            updatedTask.taskDefinition = {
              ...refTask.taskDefinition,
            };
          }

          // Sync Timeouts & Retry Policies
          if (syncTimeoutRetry) {
            if (refTask.retryCount !== undefined) updatedTask.retryCount = refTask.retryCount;
            if (refTask.timeoutSeconds !== undefined) updatedTask.timeoutSeconds = refTask.timeoutSeconds;
            if (refTask.responseTimeoutSeconds !== undefined)
              updatedTask.responseTimeoutSeconds = refTask.responseTimeoutSeconds;
            if (refTask.retryLogic) updatedTask.retryLogic = refTask.retryLogic;
            if (refTask.retryDelaySeconds !== undefined)
              updatedTask.retryDelaySeconds = refTask.retryDelaySeconds;
            if (refTask.timeoutPolicy) updatedTask.timeoutPolicy = refTask.timeoutPolicy;
          }

          return updatedTask;
        });

        // 2. Add missing tasks if addMissingTasks is enabled
        if (addMissingTasks) {
          const currentTaskNames = new Set(
            flatten(updatedTasks).map((t: any) => t.name).filter(Boolean),
          );
          const existingRefNames = new Set(
            flatten(updatedTasks).map((t: any) => t.taskReferenceName).filter(Boolean),
          );

          refTaskMap.forEach((refTask, taskName) => {
            if (!currentTaskNames.has(taskName)) {
              // Task does not exist in target workflow -> clone and append
              const clonedTask: any = JSON.parse(JSON.stringify(refTask));

              // Ensure unique taskReferenceName in target workflow
              let baseRefName = clonedTask.taskReferenceName || `${taskName}_ref_1`;
              if (existingRefNames.has(baseRefName)) {
                let suffix = 1;
                while (existingRefNames.has(`${baseRefName}_${suffix}`)) {
                  suffix++;
                }
                clonedTask.taskReferenceName = `${baseRefName}_${suffix}`;
              }
              existingRefNames.add(clonedTask.taskReferenceName);

              updatedTasks.push(clonedTask);
            }
          });
        }

        return {
          ...targetWf,
          tasks: updatedTasks,
        };
      });

      // Include reference workflow as is (unchanged) and updated target workflows
      const allWorkflowsPayload = [referenceWorkflow, ...updatedTargetWorkflows];

      // Send PUT request to Conductor metadata endpoint
      await updateWorkflowsAction.mutateAsync({
        method: "PUT",
        path: "/metadata/workflow",
        body: JSON.stringify(allWorkflowsPayload),
      });

      setIsSyncing(false);
      onSuccess(
        `Berhasil menyinkronkan & menambahkan ${selectedTaskNames.size} task pada ${targetWorkflows.length} workflow berdasarkan acuan '${referenceWorkflow.name}'.`,
      );
      onClose();
    } catch (err: any) {
      logger.error("Task sync error:", err);
      setSyncError(err?.message || "Gagal menyinkronkan definisi task ke workflow.");
      setIsSyncing(false);
    }
  };

  const isAllSelected =
    tasksComparison.length > 0 && selectedTaskNames.size === tasksComparison.length;
  const isIndeterminate =
    selectedTaskNames.size > 0 && selectedTaskNames.size < tasksComparison.length;

  return (
    <Dialog open={open} onClose={isSyncing ? undefined : onClose} maxWidth="md" fullWidth>
      <DialogTitle sx={{ m: 0, p: 2, display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <Box display="flex" alignItems="center" gap={1}>
          <SyncIcon size={24} />
          <Typography variant="h6">Sinkronisasi Task Berdasarkan Workflow Acuan</Typography>
        </Box>
        <IconButton onClick={onClose} disabled={isSyncing} size="small">
          <CloseIcon size={20} />
        </IconButton>
      </DialogTitle>
      <Divider />

      <DialogContent sx={{ p: 3 }}>
        {isSyncing && <LinearProgress sx={{ mb: 2 }} />}

        {syncError && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {syncError}
          </Alert>
        )}

        {/* Reference Workflow Picker */}
        <Paper variant="outlined" sx={{ p: 2, mb: 3, backgroundColor: "background.default" }}>
          <Typography variant="subtitle2" gutterBottom color="primary.main">
            1. Pilih Workflow Acuan (Master Reference)
          </Typography>
          <Typography variant="body2" color="textSecondary" paragraph>
            Pilih 1 workflow sebagai standar acuan. Definisi task & rumus ekspresi (seperti rumus JavaScript pada INLINE task) dari workflow ini akan disalin ke workflow lainnya.
          </Typography>

          <FormControl fullWidth size="small" sx={{ mt: 1 }}>
            <InputLabel id="select-reference-workflow-label">Workflow Acuan</InputLabel>
            <Select
              labelId="select-reference-workflow-label"
              value={referenceWorkflowName}
              label="Workflow Acuan"
              onChange={(e) => setReferenceWorkflowName(e.target.value)}
              disabled={isSyncing}
            >
              {selectedWorkflows.map((wf) => (
                <MenuItem key={wf.name} value={wf.name}>
                  <strong>{wf.name}</strong> (v{wf.version}) — {wf.tasks?.length || 0} task(s)
                </MenuItem>
              ))}
            </Select>
          </FormControl>
        </Paper>

        {/* Synchronization Settings */}
        <Paper variant="outlined" sx={{ p: 2, mb: 3, backgroundColor: "action.hover" }}>
          <Typography variant="subtitle2" gutterBottom>
            2. Pengaturan Parameter & Penambahan Task
          </Typography>
          <Box display="flex" flexDirection={{ xs: "column", sm: "row" }} gap={2} mt={1} flexWrap="wrap">
            <FormControlLabel
              control={
                <Switch
                  checked={syncExpressionAndParams}
                  onChange={(e) => setSyncExpressionAndParams(e.target.checked)}
                  size="small"
                />
              }
              label={
                <Typography variant="body2">
                  Rumus Ekspresi & Parameter (<code>expression</code> / <code>inputParameters</code>)
                </Typography>
              }
            />
            <FormControlLabel
              control={
                <Switch
                  checked={addMissingTasks}
                  onChange={(e) => setAddMissingTasks(e.target.checked)}
                  size="small"
                  color="primary"
                />
              }
              label={
                <Typography variant="body2" fontWeight={600} color="primary.main">
                  Tambahkan Task Baru ke Target Jika Belum Ada
                </Typography>
              }
            />
            <FormControlLabel
              control={
                <Switch
                  checked={syncTaskDef}
                  onChange={(e) => setSyncTaskDef(e.target.checked)}
                  size="small"
                />
              }
              label={
                <Typography variant="body2">
                  Definisi Task (<code>taskDefinition</code>)
                </Typography>
              }
            />
            <FormControlLabel
              control={
                <Switch
                  checked={syncTimeoutRetry}
                  onChange={(e) => setSyncTimeoutRetry(e.target.checked)}
                  size="small"
                />
              }
              label={
                <Typography variant="body2">
                  Timeouts & Retry Policies
                </Typography>
              }
            />
          </Box>
        </Paper>

        {/* Tasks List from Reference Workflow */}
        <Box display="flex" justifyContent="space-between" alignItems="center" mb={1}>
          <Typography variant="subtitle2">
            3. Pilih Task dari Workflow Acuan yang Ingin Disamakan ({tasksComparison.length})
          </Typography>
          <Box display="flex" gap={1}>
            <Button size="small" variant="outlined" onClick={handleSelectMismatchedOnly}>
              Pilih Beda / Belum Ada
            </Button>
          </Box>
        </Box>

        {targetWorkflows.length === 0 ? (
          <Alert severity="warning">
            Harap pilih minimal 2 workflow untuk melakukan sinkronisasi task.
          </Alert>
        ) : (
          <TableContainer component={Paper} variant="outlined" sx={{ maxHeight: 340 }}>
            <Table size="small" stickyHeader>
              <TableHead>
                <TableRow>
                  <TableCell padding="checkbox">
                    <Checkbox
                      checked={isAllSelected}
                      indeterminate={isIndeterminate}
                      onChange={(e) => handleSelectAll(e.target.checked)}
                      size="small"
                    />
                  </TableCell>
                  <TableCell>Nama Task</TableCell>
                  <TableCell>Tipe</TableCell>
                  <TableCell>Status pada {targetWorkflows.length} Target Workflow</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {tasksComparison.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={4} align="center">
                      Tidak ada task ditemukan pada workflow acuan.
                    </TableCell>
                  </TableRow>
                ) : (
                  tasksComparison.map((row) => {
                    const isChecked = selectedTaskNames.has(row.name);
                    return (
                      <React.Fragment key={row.name}>
                        <TableRow hover onClick={() => handleToggleTask(row.name)} style={{ cursor: "pointer" }}>
                          <TableCell padding="checkbox" onClick={(e) => e.stopPropagation()}>
                            <Checkbox
                              checked={isChecked}
                              onChange={() => handleToggleTask(row.name)}
                              size="small"
                            />
                          </TableCell>
                          <TableCell>
                            <Typography variant="body2" fontWeight={600}>
                              {row.name}
                            </Typography>
                          </TableCell>
                          <TableCell>
                            <Chip label={row.type} size="small" variant="outlined" color={row.type === "INLINE" ? "primary" : "default"} />
                          </TableCell>
                          <TableCell>
                            {row.status === "SYNCED" && row.notFoundCount === 0 && (
                              <Chip
                                icon={<CheckCircle size={14} />}
                                label={`Sudah Identik (${row.matchingCount}/${row.targetCount} WF)`}
                                size="small"
                                color="success"
                                variant="outlined"
                              />
                            )}
                            {row.status === "MISMATCH" && (
                              <Chip
                                icon={<WarningCircle size={14} />}
                                label={`Berbeda / Perlu Sync (${row.mismatchCount} WF beda)`}
                                size="small"
                                color="warning"
                                variant="filled"
                              />
                            )}
                            {row.notFoundCount > 0 && (
                              <Chip
                                icon={<PlusCircle size={14} />}
                                label={`Belum Ada di ${row.notFoundCount} Target WF ${addMissingTasks ? "(Akan Ditambahkan)" : ""}`}
                                size="small"
                                color={addMissingTasks ? "info" : "default"}
                                variant="filled"
                                sx={{ ml: row.status !== "SYNCED" ? 1 : 0 }}
                              />
                            )}
                          </TableCell>
                        </TableRow>

                        {/* Expression / Formula Accordion Row if available */}
                        {row.expressionPreview && (
                          <TableRow sx={{ backgroundColor: "action.hover" }}>
                            <TableCell colSpan={4} sx={{ py: 1, px: 3 }}>
                              <Accordion elevation={0} defaultExpanded={row.status === "MISMATCH" || row.notFoundCount > 0}>
                                <AccordionSummary expandIcon={<CaretDown size={14} />} sx={{ minHeight: 32, p: 0 }}>
                                  <Box display="flex" alignItems="center" gap={1}>
                                    <CodeIcon size={16} />
                                    <Typography variant="caption" color="textSecondary" fontWeight={600}>
                                      Pratinjau Rumus JavaScript ({row.name})
                                    </Typography>
                                  </Box>
                                </AccordionSummary>
                                <AccordionDetails sx={{ p: 1, pt: 0 }}>
                                  <Paper
                                    variant="outlined"
                                    sx={{
                                      p: 1.5,
                                      backgroundColor: "background.paper",
                                      fontFamily: "monospace",
                                      fontSize: "0.75rem",
                                      whiteSpace: "pre-wrap",
                                      maxHeight: 120,
                                      overflowY: "auto",
                                    }}
                                  >
                                    {row.expressionPreview}
                                  </Paper>
                                </AccordionDetails>
                              </Accordion>
                            </TableCell>
                          </TableRow>
                        )}
                      </React.Fragment>
                    );
                  })
                )}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </DialogContent>
      <Divider />

      <DialogActions sx={{ p: 2, justifyContent: "space-between" }}>
        <Typography variant="caption" color="textSecondary">
          {selectedTaskNames.size} task dipilih dari acuan '{referenceWorkflowName}'
        </Typography>
        <Box display="flex" gap={1}>
          <Button onClick={onClose} disabled={isSyncing} variant="text">
            Batal
          </Button>
          <Button
            onClick={handleExecuteSync}
            disabled={isSyncing || selectedTaskNames.size === 0 || targetWorkflows.length === 0}
            variant="contained"
            color="primary"
            startIcon={<SyncIcon />}
          >
            {isSyncing ? "Menyinkronkan..." : `Samakan Task (${selectedTaskNames.size})`}
          </Button>
        </Box>
      </DialogActions>
    </Dialog>
  );
}
