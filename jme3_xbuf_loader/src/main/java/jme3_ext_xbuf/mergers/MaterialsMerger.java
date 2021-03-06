package jme3_ext_xbuf.mergers;


import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;

import org.slf4j.Logger;

import com.jme3.asset.AssetManager;
import com.jme3.asset.AssetNotFoundException;
import com.jme3.material.MatParam;
import com.jme3.material.Material;
import com.jme3.material.MaterialDef;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Node;
import com.jme3.shader.VarType;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture.MagFilter;
import com.jme3.texture.Texture.MinFilter;
import com.jme3.texture.Texture.WrapMode;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ColorSpace;

import jme3_ext_xbuf.Merger;
import jme3_ext_xbuf.XbufContext;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.ExtensionMethod;
import lombok.extern.slf4j.Slf4j;
import xbuf.Datas.Data;
import xbuf.Materials;
import xbuf.Materials.MatProperty;
import xbuf.Primitives;
import xbuf.Primitives.Color;
import xbuf.Primitives.Texture2DInline;

@ExtensionMethod({jme3_ext_xbuf.ext.PrimitiveExt.class})

@Slf4j
public class MaterialsMerger implements Merger{
	protected final AssetManager assetManager;
	protected @Setter @Getter Texture defaultTexture;
	protected @Setter @Getter Material defaultMaterial;


	public MaterialsMerger(AssetManager assetManager) {
		this.assetManager = assetManager;
		defaultTexture = newDefaultTexture();
		defaultMaterial = newDefaultMaterial();
	}

	public Material newDefaultMaterial() {
		Material m=new Material(assetManager,"MatDefs/MatCap.j3md");
		m.setTexture("DiffuseMap",assetManager.loadTexture("Textures/generator8.jpg"));
		m.setColor("Multiply_Color",ColorRGBA.Pink);
		m.setFloat("ChessSize",0.5f);
		m.setName("DEFAULT");
		return m;
	}

	public Texture newDefaultTexture() {
		Texture t=assetManager.loadTexture("Textures/debug_8_64.png");
		t.setWrap(WrapMode.Repeat);
		t.setMagFilter(MagFilter.Nearest);
		t.setMinFilter(MinFilter.NearestLinearMipMap);
		t.setAnisotropicFilter(2);
		return t;
	}

	public Texture getValue(Node root,Primitives.Texture t) {
		Texture tex;
		switch(t.getDataCase()){
			case DATA_NOT_SET:
				tex=null;
				break;
			case RPATH:
				// Try first to load from asset path
				String path=root.getName();
				path=path.substring(0,path.lastIndexOf("/"))+"/"+t.getRpath();
				try{					
					tex=assetManager.loadTexture(path);			
				}catch(AssetNotFoundException ex1){
					log.debug("failed to load texture:",path,ex1," try with asset root.");

					// If not found load from root
					try{
						tex=assetManager.loadTexture(t.getRpath());
						
						// TODO: make an option for this in the model key
						tex.setMagFilter(MagFilter.Bilinear);
						tex.setMinFilter(MinFilter.Trilinear);
						tex.setAnisotropicFilter(4);
					}catch(AssetNotFoundException ex){
						log.warn("failed to load texture:",t.getRpath(),ex);
						tex=defaultTexture.clone();
					}
				}
				break;
			case TEX2D:{
				Texture2DInline t2di=t.getTex2D();
				//TODO read ColorSpace from xbuf data
				Image img=new Image(getValue(t2di.getFormat()),t2di.getWidth(),t2di.getHeight(),t2di.getData().asReadOnlyByteBuffer(), ColorSpace.Linear);
				tex=new Texture2D(img);
				break;
			}
			default:
				throw new IllegalArgumentException("doesn't support more than texture format:"+t.getDataCase());
		}
		tex.setWrap(WrapMode.Repeat);
		return tex;
	}

	public Image.Format getValue(Texture2DInline.Format f) {
		switch(f){
			// case bgra8: return Image.Format.BGR8;
			case rgb8:
				return Image.Format.RGB8;
			case rgba8:
				return Image.Format.RGBA8;
			default:
				throw new UnsupportedOperationException("image format :"+f);
		}
	}
	
	public void apply(Data src, Node root, XbufContext context, Logger log) {
		for(xbuf.Materials.Material m:src.getMaterialsList()){
			Material mat=new Material(assetManager,m.getMatId());
			String id=m.getId();
			context.put(id,mat);
			mat.setName(m.hasName()?m.getName():m.getId());
			List<MatProperty> properties=m.getPropertiesList();
			
			for(MatProperty p:properties){
				String name=p.getId();
				if (name.equals("RenderBucket")){
					context.put("G~"+id+"~RenderBucket",p.getValue(),id);
				}else if(p.hasValue()){
					Double d=new Double(p.getValue());
					Collection<MatParam> params=mat.getMaterialDef().getMaterialParams();
					MatParam param=null;
					for(MatParam pr:params){
						if(pr.getName().equals(name)){
							param=pr;
							break;
						}
					}
					if(param==null){
						log.warn("Parameter {}  is not available for material  {}. Skip.",name,m.getMatId());
						StringBuilder sb=new StringBuilder();
						sb.append("Available parameters:\n");
						for(Entry<String,MatParam> e:mat.getParamsMap().entrySet()){
							sb.append(e.getKey()).append(", ");
						}
						log.warn(sb.toString());
						continue;
					}
					switch(param.getVarType()){
						case Float:{
							mat.setFloat(name,d.floatValue());
							break;
						}
						case Int:{
							mat.setInt(name,d.intValue());
							break;
						}
						case Boolean:{
							mat.setBoolean(name,d.intValue()==1);
							break;
						}
						default:
					}
					
				}else if(p.hasColor()){
					mat.setColor(name,p.getColor().toJME());
				}else if(p.hasTexture()){
					Texture tx=getValue(root,p.getTexture());
					if(tx!=null){
						mat.setTexture(name,tx);
					}
				}else if(p.hasVec3()){
					mat.setVector3(name,p.getVec3().toJME());
				}else if(p.hasVec2()){
					mat.setVector2(name,p.getVec2().toJME());
				}
			}
		}
	}


}
