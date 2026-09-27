/**
 * Fetch utilities for OSS mode.
 *
 * This version removes gateway and auth token handling to connect
 * directly to a single Conductor API server.
 */
import { MessageContext } from "components/providers/messageContext";
import { useContext } from "react";
import { IObject } from "types/common";
import { getErrorMessage, tryToJson } from "utils/utils";
import { useEnv as hardcodeEnv } from "./env";

const { VITE_ENVIRONMENT, VITE_WF_SERVER } = import.meta.env;

export function fetchContextNonHook() {
  const { stack } = hardcodeEnv();

  return {
    stack,
    ready: true,
  };
}

export function useFetchContext() {
  const contextNonHook = fetchContextNonHook();
  const { setMessage } = useContext(MessageContext);

  return {
    ...contextNonHook,
    setMessage,
  };
}

export async function fetchWithContext(
  path: string,
  context: IObject,
  fetchParams: IObject,
  isText?: boolean,
  throwOnError = true,
): Promise<any> {
  const newParams = { ...fetchParams };

  const cleanSubPath = path.replace(/^\//, "").replace(/^api\//, "");
  const targetUrl =
    VITE_ENVIRONMENT === "test"
      ? `${VITE_WF_SERVER || ""}/api/${cleanSubPath}`
      : `/api/${cleanSubPath}`;

  const cleanPath = targetUrl.replace(/([^:]\/)\/+/g, "$1");

  const res = await fetch(cleanPath, newParams);

  // Handle error cases
  if (!res.ok) {
    const hasContext = context && context?.setMessage != null;
    // 1. Using global message
    if (hasContext && !throwOnError) {
      const errorMessage = await getErrorMessage(res);
      context.setMessage({ text: errorMessage, severity: "error" });

      return null;
    }

    // 2. Throw error
    if (throwOnError) {
      const errorMessage = await getErrorMessage(res);
      const errorObj = new Error(errorMessage);
      (errorObj as any).status = res.status;
      throw errorObj;
    }

    return null;
  }

  if (isText) {
    return res.text();
  }

  const text = await res.text();
  if (!text || text.length === 0) {
    return null;
  }
  return tryToJson(text);
}
