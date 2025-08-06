import React, { useCallback, useState } from "react";
import { t } from "ttag";
import {
  OpenidButtonRoot,
  AuthError,
  AuthErrorRoot,
} from "./OpenIDButton.styled";

export interface OpenidButtonProps {
  isCard?: boolean;
  isEnabled?: boolean;
}

const OpenidButton = ({
  isCard,
  isEnabled = true,
}: OpenidButtonProps) => {
  const [errors, setErrors] = useState<string[]>([]);

  const [isLoading, setIsLoading] = useState(false);

  const handleLogin = useCallback(
    async () => {
      try {
        setErrors([]);
        setIsLoading(true);

        // Call the initiate endpoint to get authorization URL
        const response = await fetch("/api/openid/auth_url", {
          method: "GET",
          headers: {
            "Content-Type": "application/json",
          },
        });

        if (!response.ok) {
          const errorData = await response.json();
          throw new Error(errorData.message || t`Failed to initiate OpenID authentication`);
        }

        const data = await response.json();

        // Store current URL for return after authentication
        const returnUrl = window.location.pathname + window.location.search;
        if (returnUrl !== "/auth/login" && returnUrl !== "/auth/callback") {
          sessionStorage.setItem("openid_return_url", returnUrl);
        }

        // Redirect to the OpenID provider
        window.location.href = data.authorization_url;
      } catch (error) {
        console.error("OpenID authentication error:", error);
        setErrors([error instanceof Error ? error.message : t`Authentication failed`]);
        setIsLoading(false);
      }
    },
  []);

  const handleError = useCallback(() => {
    setErrors([
      t`There was an issue signing in with OpenID Connect. Please contact an administrator.`,
    ]);
  }, []);

  return (
    <OpenidButtonRoot>
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
          {isLoading ? t`Redirecting...` : t`Sign in with OpenID Connect`}
        </button>
      )}

      {errors.length > 0 && (
        <AuthErrorRoot>
          {errors.map((error, index) => (
            <AuthError key={index}>{error}</AuthError>
          ))}
        </AuthErrorRoot>
      )}
    </OpenidButtonRoot>
  );
};

export default OpenidButton;
