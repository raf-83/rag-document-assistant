export type ChatSourceChunk = {
  chunkId: string;
  chunkIndex: number;
  startCharIndex: number;
  endCharIndex: number;
  score: number | null;
};

export type DocumentChatResponse = {
  documentId: string;
  answer: string;
  sources: ChatSourceChunk[];
  contextFound: boolean;
  createdAt: string;
};

export type LocalChatMessage = {
  id: string;
  role: "user" | "assistant";
  content: string;
  sources?: ChatSourceChunk[];
  contextFound?: boolean;
};
