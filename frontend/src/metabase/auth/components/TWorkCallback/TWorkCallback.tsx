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

        // Получаем access token через бэкенд
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

        const responseData = await response.json();
        const accessToken = responseData.access_token; // Получаем access token из ответа

        if (!accessToken) {
          throw new Error(t`No access token received from TWork`);
        }

        const returnUrl = sessionStorage.getItem("twork_return_url") || "/";
        sessionStorage.removeItem("twork_return_url");

        // Используем onLogin с полученным access token
        onLogin(accessToken, returnUrl);
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
        <div style={{ marginBottom: "1rem", color: "red" }}>
          {t`Authentication failed: ${error}`}
        </div>
        <button onClick={() => router.push("/auth/login")}>
          {t`Return to login`}
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
