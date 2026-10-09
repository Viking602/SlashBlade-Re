package jp.nyatla.nymmd;


import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import jp.nyatla.nymmd.types.*;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Vector;


public class MmdMotionPlayerGL2 extends MmdMotionPlayer
{

	private class Material
	{
		public float[] color;// Diffuse,Specular,Ambientの順
		public float fShininess;
		public short[] indices;
		public int ulNumIndices;
		public Identifier texture_id;
		public int unknown;
	}	
	public MmdMotionPlayerGL2()
	{
		super();
	}
	private final MmdMatrix __tmp_matrix = new MmdMatrix();
	private Material[] _materials;
	private float[] _fbuf;
	private MmdTexUV[] _tex_array;
	
	@Override
	public void setPmd(MmdPmdModel_BasicClass i_pmd_model) throws MmdException
	{
		super.setPmd(i_pmd_model);
		
		//確保済みリソースのリセット
		//OpenGLResourceの生成
		final int number_of_vertex=i_pmd_model.getNumberOfVertex();
		this._fbuf=new float[number_of_vertex*3*2];
		
		MmdPmdModel_BasicClass.IResourceProvider tp=i_pmd_model.getResourceProvider();
		
		//Material配列の作成
		PmdMaterial[] m = i_pmd_model.getMaterials();// this._ref_materials;
		Vector<Material> materials = new Vector<Material>();
		for (int i = 0; i < m.length; i++){
			final Material new_material = new Material();
			new_material.unknown=m[i].unknown;
			// D,A,S[rgba]
			float[] color = new float[12];
			m[i].col4Diffuse.getValue(color, 0);
			m[i].col4Ambient.getValue(color, 4);
			m[i].col4Specular.getValue(color, 8);

			new_material.color = color;
			/*
			= makeFloatBuffer(12);
			new_material.color.put(color);
			new_material.color.position(0);
*/

			new_material.fShininess = m[i].fShininess;

			if (m[i].texture_name != null && !m[i].texture_name.isEmpty())
			{
				new_material.texture_id = tp.getTextureStream(m[i].texture_name);
			} else {
				new_material.texture_id = null;
			}

			//new_material.indices=ShortBuffer.wrap(m[i].indices);
			new_material.indices = m[i].indices;

			new_material.ulNumIndices = m[i].indices.length;
			materials.add(new_material);
		}
		this._materials = materials.toArray(new Material[materials.size()]);

		this._tex_array = this._ref_pmd_model.getUvArray();
		return;		
	}
	public void setVmd(MmdVmdMotion_BasicClass i_vmd_model) throws MmdException
	{
		super.setVmd(i_vmd_model);
	}
	
	/**
	 * この関数はupdateMotionがskinning_matを更新するを呼び出します。
	 */
	@Override
	protected void onUpdateSkinningMatrix(MmdMatrix[] i_skinning_mat) throws MmdException
	{
		MmdVector3 vp;
		MmdMatrix mat;
		MmdVector3[] org_pos_array=this._ref_pmd_model.getPositionArray();
		MmdVector3[] org_normal_array=this._ref_pmd_model.getNormatArray();
		PmdSkinInfo[] org_skin_info=this._ref_pmd_model.getSkinInfoArray();
		
		int number_of_vertex=this._ref_pmd_model.getNumberOfVertex();
		float[] ft=this._fbuf;
		int p1=0;
		int p2=number_of_vertex*3;
		for (int i = 0; i<this._ref_pmd_model.getNumberOfVertex() ; i++)
		{
			PmdSkinInfo info_ptr=org_skin_info[i];
			if (info_ptr.fWeight == 0.0f)
			{
				mat = i_skinning_mat[info_ptr.unBoneNo_1];
			} else if (info_ptr.fWeight >= 0.9999f) {
				mat = i_skinning_mat[info_ptr.unBoneNo_0];
			} else {
				final MmdMatrix mat0 = i_skinning_mat[info_ptr.unBoneNo_0];
				final MmdMatrix mat1 = i_skinning_mat[info_ptr.unBoneNo_1];
				mat = this.__tmp_matrix;
				mat.MatrixLerp(mat0, mat1, info_ptr.fWeight);
			}
			vp=org_pos_array[i];
			ft[p1++]=((float)(vp.x * mat.m00 + vp.y * mat.m10 + vp.z * mat.m20 + mat.m30));
			ft[p1++]=((float)(vp.x * mat.m01 + vp.y * mat.m11 + vp.z * mat.m21 + mat.m31));
			ft[p1++]=((float)(vp.x * mat.m02 + vp.y * mat.m12 + vp.z * mat.m22 + mat.m32));			
			
			vp=org_normal_array[i];
			ft[p2++]=((float)(vp.x * mat.m00 + vp.y * mat.m10 + vp.z * mat.m20));
			ft[p2++]=((float)(vp.x * mat.m01 + vp.y * mat.m11 + vp.z * mat.m21));
			ft[p2++]=((float)(vp.x * mat.m02 + vp.y * mat.m12 + vp.z * mat.m22));
		}
		return;
	}
    public void render(com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, int light) {
        int normalOffset = this._ref_pmd_model.getNumberOfVertex() * 3;
        for (Material material : this._materials) {
            Identifier texture = material.texture_id != null ? material.texture_id : mods.flammpfeil.slashblade.client.renderer.model.BladeModelManager.resourceDefaultTexture;
            var output = buffers.getBuffer(mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState.getSlashBladeBlend(texture));
            for (short index : material.indices) {
                int vertex = Short.toUnsignedInt(index), pos = vertex * 3, normal = normalOffset + pos;
                output.addVertex(pose.last(), _fbuf[pos], _fbuf[pos+1], -_fbuf[pos+2])
                    .setColor(material.color[0], material.color[1], material.color[2], material.color[3])
                    .setUv(_tex_array[vertex].u, _tex_array[vertex].v).setLight(light)
                    .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                    .setNormal(pose.last(), _fbuf[normal], _fbuf[normal+1], -_fbuf[normal+2]);
            }
        }
    }

    private static FloatBuffer makeFloatBuffer(int i_size)
    {
        ByteBuffer bb = ByteBuffer.allocateDirect(i_size*4);
        bb.order(ByteOrder.nativeOrder());
        FloatBuffer fb = bb.asFloatBuffer();
        return fb;
    }
}
