import React, { useCallback, useState } from "react";
import { t } from "ttag";
import {
  TWorkButtonRoot,
  AuthError,
  AuthErrorRoot,
} from "./TWorkButton.styled";

export interface TWorkButtonProps {
  isCard?: boolean;
  isEnabled?: boolean;
}

const TWorkButton = ({
  isCard,
  isEnabled = true,
}: TWorkButtonProps) => {
  const [errors, setErrors] = useState<string[]>([]);

  const [isLoading, setIsLoading] = useState(false);

  const handleLogin = useCallback(
    async () => {
      try {
        setErrors([]);
        setIsLoading(true);

        // Call the initiate endpoint to get authorization URL
        const response = await fetch("/api/twork/auth_url", {
          method: "GET",
          headers: {
            "Content-Type": "application/json",
          },
        });

        if (!response.ok) {
          const errorData = await response.json();
          throw new Error(errorData.message || t`Failed to initiate TWork authentication`);
        }

        const data = await response.json();

        // Store current URL for return after authentication
        const returnUrl = window.location.pathname + window.location.search;
        if (returnUrl !== "/auth/login" && returnUrl !== "/auth/callback") {
          sessionStorage.setItem("twork_return_url", returnUrl);
        }

        // Redirect to the TWork provider
        window.location.href = data.authorization_url;
      } catch (error) {
        console.error("TWork authentication error:", error);
        setErrors([error instanceof Error ? error.message : t`Authentication failed`]);
        setIsLoading(false);
      }
    },
  []);

  const handleError = useCallback(() => {
    setErrors([
      t`There was an issue signing in with TWork Connect. Please contact an administrator.`,
    ]);
  }, []);

  return (
    <TWorkButtonRoot>
      {(
        <button
          onClick={handleLogin}
          onError={handleError}
          disabled={isLoading && !(isCard && isEnabled)}
          style={{
            width: "300px",
            height: "40px",
            backgroundColor: isLoading ? "#6c757d" : "#007bff",
            color: "white",
            border: "none",
            borderRadius: "4px",
            cursor: isLoading ? "not-allowed" : "pointer",
            fontSize: "14px",
            fontWeight: 500,
          }}
        >
          {isLoading ? t`Redirecting...` : t`Sign in with TWork Connect`}
        </button>
      )}

      {errors.length > 0 && (
        <AuthErrorRoot>
          {errors.map((error, index) => (
            <AuthError key={index}>{error}</AuthError>
          ))}
        </AuthErrorRoot>
      )}
    </TWorkButtonRoot>
  );
};

export default TWorkButton;
