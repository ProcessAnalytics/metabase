import React from "react";
import { withRouter } from "react-router";
import { useEffect, useState } from "react";
import { t } from "ttag";
import { connect } from "react-redux";
import { loginTWork } from "../../actions";

interface TWorkCallbackProps {
  location: {
    search: string;
  };
  router: {
    push: (path: string) => void;
  };
  onLogin: (token: string, redirectUrl?: string) => void;
}

const TWorkCallback = ({ location, router, onLogin }: TWorkCallbackProps) => {
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const handleCallback = async () => {
      try {
        const urlParams = new URLSearchParams(location.search);
        const code = urlParams.get("code");
        const error = urlParams.get("error");
        const errorDescription = urlParams.get("error_description");

        if (error) {
          throw new Error(errorDescription || error);
        }

        if (!code) {
          throw new Error(t`Authorization code is required`);
        }

        const params = new URLSearchParams({ code: code }).toString();
        const response = await fetch(`/api/session/twork/auth?${params}`, {
          method: "GET",
          headers: {
            "Content-Type": "application/json",
          },
          credentials: "include",
        });

        if (!response.ok) {
          const errorData = await response.json();
          throw new Error(errorData.message || t`Failed to process TWork callback`);
        }

        const returnUrl = sessionStorage.getItem("twork_return_url") || "/";
        sessionStorage.removeItem("twork_return_url");

        // onLogin(token, returnUrl);
        setIsLoading(false);

      } catch (err) {
        console.error("TWork callback error:", err);
        setError(err instanceof Error ? err.message : t`Authentication failed`);
        setIsLoading(false);
      }
    };

    handleCallback();
  }, [location.search, router, onLogin]);

  if (isLoading) {
    return (
      <div style={{
        display: "flex",
        justifyContent: "center",
        alignItems: "center",
        height: "100vh",
        flexDirection: "column"
      }}>
        <div style={{ marginBottom: "1rem" }}>
          {t`Processing TWork authentication...`}
        </div>
        <div className="loading-spinner" />
      </div>
    );
  }

  if (error) {
    return (
      <div style={{
        display: "flex",
        justifyContent: "center",
        alignItems: "center",
        height: "100vh",
        flexDirection: "column"
      }}>
        <div style={{
          color: "red",
          marginBottom: "1rem",
          textAlign: "center",
          maxWidth: "400px"
        }}>
          {t`Authentication failed: ${error}`}
        </div>
        <button
          onClick={() => router.push("/auth/login")}
          style={{
            padding: "0.5rem 1rem",
            backgroundColor: "#509ee3",
            color: "white",
            border: "none",
            borderRadius: "4px",
            cursor: "pointer"
          }}
        >
          {t`Return to Login`}
        </button>
      </div>
    );
  }

  return null;
};

const mapDispatchToProps = {
  onLogin: loginTWork,
};

export default connect(null, mapDispatchToProps)(withRouter(TWorkCallback));
