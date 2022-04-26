package honeybadger.protocol;

import com.google.gson.Gson;
import honeybadger.msg.ValMsg;
import io.netty.util.CharsetUtil;
import p2p.NetworkInfo;
import pojo.Node;
import pojo.msg.MsgType;
import pojo.msg.RawMsg;
import pojo.msg.ReqMsg;
import utils.ErasureCodeUtils;
import utils.MerkleTree;
import utils.SendUtils;

public class MsgProcessor {

    public static Node node = Node.getInstance();
    public static Gson gson = new Gson();

    public static void req(ReqMsg reqMsg) {

        if (!MsgValidator.isReqValid(reqMsg)) {
            return;
        }

        String req = reqMsg.getBody();

        // 为保证性能，发送val消息时，每个节点仅广播所有消息的一部分，最后取所有节点proposal的并集作为共识结果
        String proposed = req.substring(0, (int) Math.ceil(req.length() / NetworkInfo.getN()));

        // TODO：进行纠删编码，保证其他只要收到N-2f个val消息就能恢复内容
        byte[][] erasureEncoded = ErasureCodeUtils.encode(proposed.getBytes(CharsetUtil.UTF_8));

        // TODO：阈值加密，保证隐私性

        // 构造merkle树
        MerkleTree merkleTree = new MerkleTree(erasureEncoded, node.getDigestAlgorithm());

        // 生成proof
        byte[] data = erasureEncoded[node.getIndex()];
        byte[] root = merkleTree.getRoot();
        byte[][] proof = merkleTree.getProof(node.getIndex());

        // 生成VAL消息
        ValMsg valMsg = new ValMsg(data, root, proof);
        String json = gson.toJson(valMsg);
        RawMsg rawMsg = new RawMsg(MsgType.VAL, json, null);

        // 广播消息（包括自己）
        SendUtils.publishToServer(rawMsg);

        // 回收ConsensusStatus中不再需要的记录
        MsgGC.afterReq();

    }

    public static void val(ValMsg valMsg) {

    }
}
