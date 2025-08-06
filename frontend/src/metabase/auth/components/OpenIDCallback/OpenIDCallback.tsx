import React from "react";
import { withRouter } from "react-router";
import { useEffect, useState } from "react";
import { t } from "ttag";

interface OpenIDCallbackProps {
  location: {
    search: string;
  };
  router: {
    push: (path: string) => void;
  };
}

const OpenIDCallback = ({ location, router }: OpenIDCallbackProps) => {
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const handleCallback = async () => {
      try {
        // Получаем параметры из URL
        const urlParams = new URLSearchParams(location.search);
        const code = urlParams.get("code");
        const error = urlParams.get("error");
        const errorDescription = urlParams.get("error_description");

        // Проверяем на ошибки от провайдера
        if (error) {
          throw new Error(errorDescription || error);
        }

        if (!code) {
          throw new Error(t`Authorization code is required`);
        }

        const params = new URLSearchParams({ code: code }).toString();
        const response = await fetch(`/api/openid/callback?${params}`, {
          method: "GET",
          headers: {
            "Content-Type": "application/json",
          },
        });

        if (!response.ok) {
          const errorData = await response.json();
          throw new Error(errorData.message || t`Failed to process OpenID callback`);
        }

        // Получаем данные ответа
        const data = await response.json();

        // Сохраняем access_token в sessionStorage для использования в заголовках
        if (data.access_token) {
          sessionStorage.setItem("openid_access_token", data.access_token);
        }

        // Перенаправляем на главную страницу или сохраненный URL
        const returnUrl = sessionStorage.getItem("openid_return_url") || "/";
        sessionStorage.removeItem("openid_return_url");
        router.push(returnUrl);

      } catch (err) {
        console.error("OpenID callback error:", err);
        setError(err instanceof Error ? err.message : t`Authentication failed`);
        setIsLoading(false);
      }
    };

    handleCallback();
  }, [location.search, router]);

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
          {t`Processing OpenID authentication...`}
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

export default withRouter(OpenIDCallback);
