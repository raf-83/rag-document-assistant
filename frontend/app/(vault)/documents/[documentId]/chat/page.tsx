import { ChatWorkspace } from "@/features/chat/chat-workspace";

export default async function ChatPage({ params }: { params: Promise<{ documentId: string }> }) {
  const { documentId } = await params;
  return <ChatWorkspace documentId={documentId} />;
}
