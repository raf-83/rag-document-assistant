export type ApiErrorResponse = {
  timestamp?: string;
  status: number;
  error?: string;
  message: string;
  path?: string;
};

export type ApiMessageResponse = {
  message: string;
};
