import styled from "@emotion/styled";
import { color } from "metabase/lib/colors";

export const OpenidButtonRoot = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem;
`;

export const AuthErrorRoot = styled.div`
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
`;

export const AuthError = styled.div`
  color: ${color("error")};
  font-size: 0.875rem;
  text-align: center;
`;

export const TextLink = styled.a`
  color: ${color("brand")};
  text-decoration: none;
  font-weight: 500;

  &:hover {
    text-decoration: underline;
  }
`;
