package com.github.rfdetoni.bjorm.processor;

import com.github.rfdetoni.bjorm.*;
import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.*;
import javax.tools.Diagnostic;
import javax.tools.StandardLocation;
import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

/** Generates direct JDBC mappers, typed paths, projection readers and static SQL repository implementations. */
@SupportedAnnotationTypes({"com.github.rfdetoni.bjorm.Table", "com.github.rfdetoni.bjorm.Projection", "com.github.rfdetoni.bjorm.Query"})
public final class EntityProcessor extends AbstractProcessor {
    private static final Pattern IDENT=Pattern.compile("[A-Za-z_][A-Za-z_0-9]*");
    private final Set<String> generated=new HashSet<>();
    private final Set<String> mapperServices=new TreeSet<>();
    private boolean servicesWritten;
    private Types types;
    private Elements elements;
    @Override public synchronized void init(ProcessingEnvironment env) {
        super.init(env);types=env.getTypeUtils();elements=env.getElementUtils();
    }
    @Override public SourceVersion getSupportedSourceVersion(){return SourceVersion.latestSupported();}
    @Override public boolean process(Set<? extends TypeElement> annotations,RoundEnvironment round) {
        for(Element el:round.getElementsAnnotatedWith(Table.class)) {
            if(!(el instanceof TypeElement t))continue;
            try {entity(t);}catch(IOException|IllegalArgumentException e){error(el,e.getMessage());}
        }
        for(Element el:round.getElementsAnnotatedWith(Projection.class)) {
            if(!(el instanceof TypeElement t))continue;
            try {projection(t);}catch(IOException|IllegalArgumentException e){error(el,e.getMessage());}
        }
        Set<TypeElement> repositories=new LinkedHashSet<>();
        for(Element el:round.getElementsAnnotatedWith(Query.class)) {
            if(el.getKind()==ElementKind.METHOD && el.getEnclosingElement() instanceof TypeElement t)repositories.add(t);
            else error(el,"@Query must annotate an interface method");
        }
        for(TypeElement t:repositories)try{repository(t);}catch(IOException|IllegalArgumentException e){error(t,e.getMessage());}
        if(round.processingOver() && !servicesWritten && !mapperServices.isEmpty()) {
            servicesWritten=true;
            try(Writer w=processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT,"","META-INF/services/com.github.rfdetoni.bjorm.EntityMapper").openWriter()){
                for(String name:mapperServices)w.write(name+"\n");
            }catch(IOException e){error(null,"Could not write generated mapper service index: "+e.getMessage());}
        }
        return true;
    }
    private record Col(String property,String column,String type,String read,String write,boolean id,boolean version,boolean generated,boolean json,boolean uuidV7) {}
    private record Relation(String property,String mappedBy,String childType,String getter,String childGetter,String childSetter,boolean childRecord) {}
    private void entity(TypeElement entity) throws IOException {
        if(!generated.add(entity.getQualifiedName()+"#entity"))return;
        if(entity.getNestingKind().isNested() || !entity.getModifiers().contains(Modifier.PUBLIC)) {error(entity,"@Table requires public top-level type");return;}
        boolean record=entity.getKind()==ElementKind.RECORD;
        if(!record && entity.getKind()!=ElementKind.CLASS) {error(entity,"@Table requires record or class");return;}
        String table=entity.getAnnotation(Table.class).value();
        if(!identifier(table)){error(entity,"Invalid table identifier: "+table);return;}
        List<Col> cols=new ArrayList<>();
        List<Relation> relations=new ArrayList<>();
        if(record){
            for(RecordComponentElement c:entity.getRecordComponents()){
                String prop=c.getSimpleName().toString();
                if(c.getAnnotation(Children.class)!=null) {relations.add(relation(c,"value."+prop+"()"));continue;}
                Id idAnn=c.getAnnotation(Id.class);
                cols.add(new Col(prop,colName(c,prop),c.asType().toString(),"value."+prop+"()","",idAnn!=null,c.getAnnotation(Version.class)!=null,idAnn!=null&&idAnn.generated(),c.getAnnotation(Json.class)!=null,idAnn!=null&&idAnn.uuidV7()));
            }
        } else {
            boolean constructor=false;
            for(Element el:entity.getEnclosedElements()) if(el.getKind()==ElementKind.CONSTRUCTOR && el instanceof ExecutableElement e && e.getParameters().isEmpty() && e.getModifiers().contains(Modifier.PUBLIC))constructor=true;
            if(!constructor){error(entity,"POJO needs a public no-argument constructor");return;}
            for(Element el:entity.getEnclosedElements())if(el.getKind()==ElementKind.FIELD && !el.getModifiers().contains(Modifier.STATIC)){
                String prop=el.getSimpleName().toString(),type=el.asType().toString();
                String suff=Character.toUpperCase(prop.charAt(0))+prop.substring(1);
                String getter=findGetter(entity,suff,type);
                if(el.getAnnotation(Children.class)!=null) {
                    if(getter==null) {error(el,"@Children needs public getter for "+prop);return;}
                    relations.add(relation(el,"value."+getter+"()"));continue;
                }
                String setter="set"+suff;
                if(getter==null||!hasSetter(entity,setter,type)) {error(el,"POJO needs public getter and setter for "+prop);return;}
                Id idAnn=el.getAnnotation(Id.class);
                cols.add(new Col(prop,colName(el,prop),type,"value."+getter+"()","value."+setter,cAnnotated(el,Id.class),cAnnotated(el,Version.class),idAnn!=null&&idAnn.generated(),el.getAnnotation(Json.class)!=null,idAnn!=null&&idAnn.uuidV7()));
            }
        }
        if(cols.isEmpty()){error(entity,"@Table requires mapped properties");return;}
        int id=-1,ver=-1;
        Set<String> seen=new HashSet<>();
        for(int i=0;i<cols.size();i++){
            Col c=cols.get(i);
            if(!identifier(c.column())||!seen.add(c.column())){error(entity,"Invalid/duplicate column: "+c.column());return;}
            if(!supported(c.type())) {error(entity,"Unsupported JDBC property: "+c.type());return;}
            if(c.json() && (!c.type().equals("java.lang.String") || c.id() || c.version())){error(entity,"@Json requires a non-ID String column");return;}
            if(c.generated() && (!c.id() || record)){error(entity,"Generated IDs require @Id on a mutable POJO");return;}
            if(c.uuidV7() && (!c.id()||record||c.generated()||!c.type().equals("java.util.UUID"))){error(entity,"@Id(uuidV7=true) needs a mutable UUID field without generated=true");return;}
            if(c.id()){if(id>=0){error(entity,"Exactly one @Id required");return;}id=i;}
            if(c.version()){if(ver>=0||c.id()||!Set.of("int","long","java.lang.Integer","java.lang.Long").contains(c.type())){error(entity,"@Version must annotate one integer/long non-id field");return;}ver=i;}
        }
        if(id<0){error(entity,"Missing @Id");return;}
        if(cols.size()==1){error(entity,"Entity needs a non-id property");return;}
        for(Relation r:relations) {
            TypeElement child=elements.getTypeElement(r.childType());
            if(child==null||child.getAnnotation(Table.class)==null) {error(entity,"@Children target must be a @Table entity: "+r.childType());return;}
            if(!r.childGetter().equals(cols.get(id).type())) {error(entity,"@Children mappedBy property must have the same type as parent ID: "+r.mappedBy());return;}
        }
        generateMapper(entity,table,cols,id,ver,record,relations);
        mapperServices.add(packageOf(entity)+"."+entity.getSimpleName()+"_BjormMapper");
        generatePaths(entity,cols);
    }
    private Relation relation(Element field,String getter) {
        Children ann=field.getAnnotation(Children.class);
        String mappedBy=ann.mappedBy();
        if(!identifier(mappedBy))throw new IllegalArgumentException("Invalid @Children mappedBy: "+mappedBy);
        if(!(field.asType() instanceof DeclaredType dt) || dt.getTypeArguments().size()!=1)
            throw new IllegalArgumentException("@Children requires java.util.List<Child> or Collection<Child>");
        String raw=((TypeElement)dt.asElement()).getQualifiedName().toString();
        if(!raw.equals("java.util.List") && !raw.equals("java.util.Collection"))
            throw new IllegalArgumentException("@Children requires List or Collection, not "+raw);
        TypeMirror elemType=dt.getTypeArguments().getFirst();
        if(!(elemType instanceof DeclaredType childType))throw new IllegalArgumentException("@Children type must be a concrete entity");
        TypeElement child=(TypeElement)childType.asElement();
        String childPropertyType=null, childGetter=null, childSetter=null;
        boolean record=child.getKind()==ElementKind.RECORD;
        for(Element property:record ? child.getRecordComponents():child.getEnclosedElements()) {
            if(property.getSimpleName().contentEquals(mappedBy) && (record||property.getKind()==ElementKind.FIELD)) {
                childPropertyType=property.asType().toString();
                String cap=Character.toUpperCase(mappedBy.charAt(0))+mappedBy.substring(1);
                childGetter=record?mappedBy:findGetter(child,cap,childPropertyType);
                childSetter=record?null:"set"+cap;
                if(childGetter==null||(!record&&!hasSetter(child,childSetter,childPropertyType)))
                    throw new IllegalArgumentException("@Children child FK needs public getter and setter: "+mappedBy);
                break;
            }
        }
        if(childPropertyType==null)throw new IllegalArgumentException("@Children unmapped child property: "+mappedBy);
        return new Relation(field.getSimpleName().toString(),mappedBy,child.getQualifiedName().toString(),getter,childPropertyType,childSetter,record);
    }
    private static boolean cAnnotated(Element el,Class<? extends java.lang.annotation.Annotation> ann){return el.getAnnotation(ann)!=null;}
    private String findGetter(TypeElement entity,String suff,String type){
        for(Element el:entity.getEnclosedElements())if(el.getKind()==ElementKind.METHOD&&el instanceof ExecutableElement m && m.getModifiers().contains(Modifier.PUBLIC) && m.getParameters().isEmpty() && m.getReturnType().toString().equals(type)){
            if(m.getSimpleName().contentEquals("get"+suff)||m.getSimpleName().contentEquals("is"+suff))return m.getSimpleName().toString();
        }
        return null;
    }
    private boolean hasSetter(TypeElement entity,String name,String type){
        for(Element el:entity.getEnclosedElements())if(el.getKind()==ElementKind.METHOD&&el instanceof ExecutableElement m && m.getModifiers().contains(Modifier.PUBLIC) && m.getSimpleName().contentEquals(name)&&m.getParameters().size()==1&&m.getParameters().getFirst().asType().toString().equals(type))return true;
        return false;
    }
    private String colName(Element el,String fallback){Column ann=el.getAnnotation(Column.class);return ann!=null?ann.value():fallback;}
    private void generateMapper(TypeElement entity,String table,List<Col> cols,int id,int version,boolean record,List<Relation> relations) throws IOException {
        String clazz=entity.getSimpleName().toString(),pkg=packageOf(entity),mapper=clazz+"_BjormMapper";
        String fields=String.join(", ",cols.stream().map(Col::column).toList());
        List<Col> insertCols=cols.stream().filter(c->!c.generated()).toList();
        String insert=insertCols.isEmpty()?"INSERT INTO "+table+" DEFAULT VALUES":"INSERT INTO "+table+" ("+String.join(", ",insertCols.stream().map(Col::column).toList())+") VALUES ("+String.join(", ",insertCols.stream().map(c->c.json()?"CAST(? AS jsonb)":"?").toList())+")";
        List<String> assignments=new ArrayList<>();
        for(int i=0;i<cols.size();i++) if(i!=id){Col c=cols.get(i);if(i==version)assignments.add(c.column()+" = "+c.column()+" + 1");else assignments.add(c.column()+" = "+(c.json()?"CAST(? AS jsonb)":"?"));}
        String where=cols.get(id).column()+" = ?"+(version>=0?" AND "+cols.get(version).column()+" = ?":"");
        String update="UPDATE "+table+" SET "+String.join(", ",assignments)+" WHERE "+where;
        String delete="DELETE FROM "+table+" WHERE "+where;
        String select="SELECT "+fields+" FROM "+table;
        String upsert=null;
        if(!cols.get(id).generated()) {
            List<String> updateSet=new ArrayList<>();
            for(int i=0;i<cols.size();i++)if(i!=id) {
                Col c=cols.get(i);
                updateSet.add(c.column()+" = "+(i==version?table+"."+c.column()+" + 1":"EXCLUDED."+c.column()));
            }
            upsert=insert+" ON CONFLICT ("+cols.get(id).column()+") DO UPDATE SET "+String.join(", ",updateSet)
                +(version<0?"":" WHERE "+table+"."+cols.get(version).column()+" = EXCLUDED."+cols.get(version).column());
        }

        try(Writer w=file(pkg,mapper,entity)){
            w.write("package "+pkg+";\npublic final class "+mapper+" implements com.github.rfdetoni.bjorm.EntityMapper<"+clazz+"> {\n");
            w.write("public static final "+mapper+" INSTANCE = new "+mapper+"();\npublic "+mapper+"(){}\n");
            w.write("public Class<"+clazz+"> type(){return "+clazz+".class;}\n");
            w.write("public String table(){return \""+table+"\";}\n");
            w.write("public String columnFor(String property){return switch(property){\n");
            for(Col c:cols)w.write("case \""+c.property()+"\" -> \""+c.column()+"\";\n");
            w.write("default -> throw new IllegalArgumentException(\"Unknown mapped property: \"+property);};}\n");
            w.write("public Object readProperty(java.sql.ResultSet rs,int index,String property) throws java.sql.SQLException {return switch(property){\n");
            for(Col c:cols)w.write("case \""+c.property()+"\" -> "+reader(123456789,c.type()).replace("123456789","index")+";\n");
            w.write("default -> throw new IllegalArgumentException(\"Unknown mapped property: \"+property);};}\n");

            w.write("public String qualifiedColumns(String alias){return "+String.join("+\", \"+",cols.stream().map(c->"alias+\"."+c.column()+"\"").toList())+";}\n");
            w.write("public boolean optimisticLocking(){return "+(version>=0)+";}\n");
            w.write("public boolean generatedId(){return "+cols.get(id).generated()+";}\n");
            if(upsert!=null)w.write("public String upsertSql(){return \""+upsert+"\";}\n");
            if(cols.get(id).uuidV7())w.write("public void prepareInsert("+clazz+" value){if("+cols.get(id).read()+"==null)"+cols.get(id).write()+"(com.github.rfdetoni.bjorm.UuidV7.next());}\n");
            if(!relations.isEmpty()) {
                w.write("public java.util.List<com.github.rfdetoni.bjorm.ChildRelation<"+clazz+">> children(){return RELATIONS;}\n");
                w.write("private static final java.util.List<com.github.rfdetoni.bjorm.ChildRelation<"+clazz+">> RELATIONS=java.util.List.of(\n");
                for(int ri=0;ri<relations.size();ri++) {
                    Relation r=relations.get(ri);
                    TypeElement child=elements.getTypeElement(r.childType());
                    String getForeign=r.childRecord()?"value."+r.mappedBy()+"()":"value."+findGetter(child,Character.toUpperCase(r.mappedBy().charAt(0))+r.mappedBy().substring(1),r.childGetter())+"()";
                    w.write((ri>0?",\n":"")+"new com.github.rfdetoni.bjorm.ChildRelation<"+clazz+">(){\n");
                    w.write("public Class<?> childType(){return "+r.childType()+".class;}\n");
                    w.write("public String mappedBy(){return \""+r.mappedBy()+"\";}\n");
                    w.write("public Iterable<?> children("+clazz+" value){return "+r.getter()+";}\n");
                    w.write("public void attach(Object id,Object child){"+r.childType()+" value=("+r.childType()+")child;\n");
                    w.write("if("+getForeign+"!=null && !java.util.Objects.equals("+getForeign+",id))throw new IllegalArgumentException(\"Foreign key conflicts with parent ID\");\n");
                    if(r.childRecord())w.write("if(!java.util.Objects.equals("+getForeign+",id))throw new IllegalArgumentException(\"Record child must declare the parent ID\");\n");
                    else w.write("value."+r.childSetter()+"(("+r.childGetter()+")id);\n");
                    w.write("}\n}");
                }
                w.write(");\n");
            }

            if(cols.get(id).generated())w.write("public void acceptGeneratedId(java.sql.ResultSet rs,"+clazz+" value) throws java.sql.SQLException {"+cols.get(id).write()+"("+reader(1,cols.get(id).type())+");}\n");
            for(var e:List.of(new String[]{"insertSql",insert},new String[]{"updateSql",update},new String[]{"deleteSql",delete},new String[]{"selectSql",select+" WHERE "+cols.get(id).column()+" = ?"},new String[]{"selectAllSql",select}))
                w.write("public String "+e[0]+"(){return \""+e[1]+"\";}\n");
            w.write("public void bindInsert(java.sql.PreparedStatement ps,"+clazz+" value) throws java.sql.SQLException {\n");
            int insIndex=1;for(Col c:cols)if(!c.generated())w.write(setter(insIndex++,c.read(),c.type())+"\n");
            w.write("}\npublic void bindUpdate(java.sql.PreparedStatement ps,"+clazz+" value) throws java.sql.SQLException {\n");
            int index=1;
            for(int i=0;i<cols.size();i++)if(i!=id&&i!=version)w.write(setter(index++,cols.get(i).read(),cols.get(i).type())+"\n");
            w.write(setter(index++,cols.get(id).read(),cols.get(id).type())+"\n");
            if(version>=0)w.write(setter(index++,cols.get(version).read(),cols.get(version).type())+"\n");
            w.write("}\npublic void bindDelete(java.sql.PreparedStatement ps,"+clazz+" value) throws java.sql.SQLException {\n");
            w.write(setter(1,cols.get(id).read(),cols.get(id).type())+"\n");
            if(version>=0)w.write(setter(2,cols.get(version).read(),cols.get(version).type())+"\n");
            w.write("}\npublic void bindId(java.sql.PreparedStatement ps,int index,Object id) throws java.sql.SQLException {ps.setObject(index,id);}\n");
            w.write("public Object id("+clazz+" value){return "+cols.get(id).read()+";}\n");
            w.write("public "+clazz+" read(java.sql.ResultSet rs) throws java.sql.SQLException {\n");
            if(record){
                List<String> args=new ArrayList<>();int offset=1;
                for(RecordComponentElement component:entity.getRecordComponents()){
                    if(component.getAnnotation(Children.class)!=null)args.add("java.util.List.of()");
                    else args.add(reader(offset++,component.asType().toString()));
                }
                w.write("return new "+clazz+"("+String.join(", ",args)+");\n");
            }
            else {w.write(clazz+" value = new "+clazz+"();\n");for(int i=0;i<cols.size();i++)w.write(cols.get(i).write()+"("+reader(i+1,cols.get(i).type())+");\n");w.write("return value;\n");}
            w.write("}\n}\n");
        }
    }
    private void generatePaths(TypeElement entity,List<Col> cols)throws IOException {
        String clazz=entity.getSimpleName().toString();
        try(Writer w=file(packageOf(entity),clazz+"_",entity)) {
            w.write("package "+packageOf(entity)+";\npublic final class "+clazz+"_ {private "+clazz+"_(){}\n");
            for(Col c:cols)w.write("public static final com.github.rfdetoni.bjorm.Field<"+boxed(c.type())+"> "+c.property()+" = new com.github.rfdetoni.bjorm.Field<>(\""+c.column()+"\");\n");
            w.write("}\n");
        }
    }
    private void projection(TypeElement type) throws IOException {
        if(!generated.add(type.getQualifiedName()+"#projection"))return;
        if(type.getKind()!=ElementKind.RECORD||type.getNestingKind().isNested()||!type.getModifiers().contains(Modifier.PUBLIC)) {error(type,"@Projection requires public top-level record");return;}
        String name=type.getSimpleName().toString(),pkg=packageOf(type),out=name+"_BjormRowMapper";
        List<String> args=new ArrayList<>();int i=1;
        for(RecordComponentElement c:type.getRecordComponents()){
            if(!supported(c.asType().toString())) {error(c,"Unsupported projection component: "+c.asType());return;}
            args.add(reader(i++,c.asType().toString()));
        }
        try(Writer w=file(pkg,out,type)){
            w.write("package "+pkg+";\npublic final class "+out+" implements com.github.rfdetoni.bjorm.RowMapper<"+name+"> {\n");
            w.write("public static final "+out+" INSTANCE=new "+out+"();\nprivate "+out+"(){}\n");
            w.write("public "+name+" read(java.sql.ResultSet rs) throws java.sql.SQLException {return new "+name+"("+String.join(", ",args)+");}\n}\n");
        }
    }
    private void repository(TypeElement repository) throws IOException {
        if(!generated.add(repository.getQualifiedName()+"#repository"))return;
        if(repository.getKind()!=ElementKind.INTERFACE||repository.getNestingKind().isNested()||!repository.getModifiers().contains(Modifier.PUBLIC)){error(repository,"@Query requires public top-level interface");return;}
        String name=repository.getSimpleName().toString(),pkg=packageOf(repository),out=name+"_Bjorm";
        StringBuilder body=new StringBuilder();
        for(Element el:repository.getEnclosedElements())if(el.getKind()==ElementKind.METHOD){
            ExecutableElement method=(ExecutableElement)el;
            Query query=method.getAnnotation(Query.class);
            if(query==null) {if(!method.getModifiers().contains(Modifier.DEFAULT))error(method,"All abstract repository methods need @Query");continue;}
            buildMethod(method,query.value(),body);
        }
        try(Writer w=file(pkg,out,repository)){
            w.write("package "+pkg+";\npublic final class "+out+" implements "+name+" {\n");
            w.write("private final com.github.rfdetoni.bjorm.Operations db;\npublic "+out+"(com.github.rfdetoni.bjorm.Operations db){this.db=java.util.Objects.requireNonNull(db);}\n");
            w.write(body.toString());w.write("}\n");
        }
    }
    private void buildMethod(ExecutableElement method,String sql,StringBuilder code){
        Map<String,Integer> params=new LinkedHashMap<>();
        List<? extends VariableElement> vars=method.getParameters();
        for(int i=0;i<vars.size();i++){
            VariableElement p=vars.get(i);Param name=p.getAnnotation(Param.class);String key=name!=null?name.value():p.getSimpleName().toString();
            if(!identifier(key)||params.putIfAbsent(key,i)!=null){error(p,"Invalid or duplicate named parameter: "+key);return;}
            if(!supported(p.asType().toString())){error(p,"Unsupported parameter type: "+p.asType());return;}
        }
        Compiled compiled;
        try{compiled=compileSql(sql);}catch(IllegalArgumentException e){error(method,e.getMessage());return;}
        for(String name:compiled.names)if(!params.containsKey(name)){error(method,"Unknown SQL parameter :"+name);return;}
        for(String name:params.keySet())if(!compiled.names.contains(name)){error(method,"Unused SQL parameter :"+name);return;}
        TypeMirror returnType=method.getReturnType();String returnText=returnType.toString();
        boolean many=false,optional=false,write=returnType.getKind()==TypeKind.INT && sql.stripLeading().toUpperCase(Locale.ROOT).matches("(?s)^(INSERT|UPDATE|DELETE|MERGE)\\b.*");
        String rowType=returnText;
        if(returnType instanceof DeclaredType declared){
            String raw=((TypeElement)declared.asElement()).getQualifiedName().toString();
            if(raw.equals("java.util.List")||raw.equals("java.util.Optional")){
                if(declared.getTypeArguments().size()!=1){error(method,"List/Optional requires one type argument");return;}
                many=raw.equals("java.util.List");optional=!many;rowType=declared.getTypeArguments().getFirst().toString();
            }
        }
        String mapperName=null;
        if(!write){
            mapperName=scalarReader(rowType);
            if(mapperName==null){
                TypeElement target=elements.getTypeElement(rowType);
                if(target==null||target.getNestingKind().isNested()||(!hasAnnotation(target,Table.class)&&!hasAnnotation(target,Projection.class))){error(method,"@Query return requires supported scalar, @Table or @Projection top-level type: "+rowType);return;}
                mapperName=rowType+(hasAnnotation(target,Table.class)?"_BjormMapper.INSTANCE":"_BjormRowMapper.INSTANCE");
            }
        }
        code.append("@Override public ").append(returnText).append(" ").append(method.getSimpleName()).append("(");
        for(int i=0;i<vars.size();i++){if(i>0)code.append(", ");code.append(vars.get(i).asType()).append(" ").append(vars.get(i).getSimpleName());}
        code.append("){\n");
        String binder="ps -> {";
        for(int i=0;i<compiled.names.size();i++){
            VariableElement variable=vars.get(params.get(compiled.names.get(i)));
            binder+=setter(i+1,variable.getSimpleName().toString(),variable.asType().toString());
        }
        binder+="}";
        String executable="\""+javaString(compiled.sql)+"\"";
        if(write)code.append("return db.execute(").append(executable).append(", ").append(binder).append(");\n");
        else if(many)code.append("return db.query(").append(executable).append(", ").append(binder).append(", ").append(mapperName).append(");\n");
        else if(optional)code.append("return java.util.Optional.ofNullable(db.one(").append(executable).append(", ").append(binder).append(", ").append(mapperName).append("));\n");
        else code.append("return db.one(").append(executable).append(", ").append(binder).append(", ").append(mapperName).append(");\n");
        code.append("}\n");
    }
    private String scalarReader(String type){
        return switch(type){
            case "int","java.lang.Integer" -> "(rs -> rs.getObject(1, java.lang.Integer.class))";
            case "long","java.lang.Long" -> "(rs -> rs.getObject(1, java.lang.Long.class))";
            case "short","java.lang.Short" -> "(rs -> rs.getObject(1, java.lang.Short.class))";
            case "boolean","java.lang.Boolean" -> "(rs -> rs.getObject(1, java.lang.Boolean.class))";
            case "java.lang.String" -> "(rs -> rs.getString(1))";
            case "java.math.BigDecimal" -> "(rs -> rs.getBigDecimal(1))";
            case "java.util.UUID" -> "(rs -> rs.getObject(1, java.util.UUID.class))";
            default -> null;
        };
    }
    private static boolean hasAnnotation(TypeElement el,Class<? extends java.lang.annotation.Annotation> cls){return el.getAnnotation(cls)!=null;}
    private record Compiled(String sql,List<String> names){}
    /** Ignore placeholders in SQL literals/comments and PostgreSQL casts. Identifiers validated by parser. */
    private static Compiled compileSql(String sql){
        List<String> names=new ArrayList<>();StringBuilder out=new StringBuilder();
        int mode=0;String dollar=null;
        for(int i=0;i<sql.length();){char c=sql.charAt(i),next=i+1<sql.length()?sql.charAt(i+1):'\0';
            if(mode==4){if(c=='\n')mode=0;out.append(c);i++;continue;}
            if(mode==5){if(c=='*'&&next=='/'){out.append("*/");i+=2;mode=0;}else{out.append(c);i++;}continue;}
            if(mode==3){if(sql.startsWith(dollar,i)){out.append(dollar);i+=dollar.length();mode=0;}else{out.append(c);i++;}continue;}
            if(mode==1||mode==2){out.append(c);i++;if(c==(mode==1?'\'':'"')){if(next==c){out.append(next);i++;}else mode=0;}continue;}
            if(c=='\''||c=='"'){mode=c=='\''?1:2;out.append(c);i++;continue;}
            if(c=='-'&&next=='-'){mode=4;out.append("--");i+=2;continue;}
            if(c=='/'&&next=='*'){mode=5;out.append("/*");i+=2;continue;}
            if(c=='$') {int end=sql.indexOf('$',i+1);if(end>i && sql.substring(i+1,end).matches("[A-Za-z_0-9]*")){dollar=sql.substring(i,end+1);out.append(dollar);i=end+1;mode=3;continue;}}
            if(c==':' && next==':'){out.append("::");i+=2;continue;}
            if(c==':' && i>0 && sql.charAt(i-1)==':'){out.append(c);i++;continue;}
            if(c==':' && (Character.isLetter(next)||next=='_')){
                int j=i+2;while(j<sql.length()&&(Character.isLetterOrDigit(sql.charAt(j))||sql.charAt(j)=='_'))j++;
                names.add(sql.substring(i+1,j));out.append('?');i=j;continue;
            }
            out.append(c);i++;
        }
        if(mode==1||mode==2||mode==3||mode==5)throw new IllegalArgumentException("Unclosed SQL string/comment in @Query");
        return new Compiled(out.toString(),List.copyOf(names));
    }
    private boolean supported(String type){return switch(type){
        case "java.lang.String","java.util.UUID","int","java.lang.Integer","long","java.lang.Long","short","java.lang.Short","float","java.lang.Float","double","java.lang.Double","boolean","java.lang.Boolean","java.math.BigDecimal","java.time.LocalDate","java.time.LocalDateTime","java.time.Instant","java.time.OffsetDateTime"->true;
        default->{TypeElement e=elements.getTypeElement(type);yield e!=null&&e.getKind()==ElementKind.ENUM;}
    };}
    private String setter(int i,String access,String type){return switch(type){
        case "int"->"ps.setInt("+i+","+access+");";
        case "long"->"ps.setLong("+i+","+access+");";
        case "short"->"ps.setShort("+i+","+access+");";
        case "float"->"ps.setFloat("+i+","+access+");";
        case "double"->"ps.setDouble("+i+","+access+");";
        case "boolean"->"ps.setBoolean("+i+","+access+");";
        case "java.lang.String"->"ps.setString("+i+","+access+");";
        case "java.math.BigDecimal"->"ps.setBigDecimal("+i+","+access+");";
        default -> {TypeElement e=elements.getTypeElement(type);
            yield "ps.setObject("+i+","+(e!=null&&e.getKind()==ElementKind.ENUM?"("+access+"==null?null:"+access+".name())":access)+");";}
    };}
    private String reader(int i,String type){return switch(type){
        case "int"->"com.github.rfdetoni.bjorm.JdbcValues.requiredInt(rs,"+i+")";
        case "long"->"com.github.rfdetoni.bjorm.JdbcValues.requiredLong(rs,"+i+")";
        case "short"->"com.github.rfdetoni.bjorm.JdbcValues.requiredShort(rs,"+i+")";
        case "float"->"com.github.rfdetoni.bjorm.JdbcValues.requiredFloat(rs,"+i+")";
        case "double"->"com.github.rfdetoni.bjorm.JdbcValues.requiredDouble(rs,"+i+")";
        case "boolean"->"com.github.rfdetoni.bjorm.JdbcValues.requiredBoolean(rs,"+i+")";
        case "java.lang.String"->"rs.getString("+i+")";
        case "java.math.BigDecimal"->"rs.getBigDecimal("+i+")";
        default -> {TypeElement e=elements.getTypeElement(type);yield e!=null&&e.getKind()==ElementKind.ENUM ? "com.github.rfdetoni.bjorm.JdbcValues.enumValue(rs,"+i+","+type+".class)" : "rs.getObject("+i+","+boxed(type)+".class)";}
    };}
    private static String boxed(String type){return switch(type){case "int"->"java.lang.Integer";case "long"->"java.lang.Long";case "boolean"->"java.lang.Boolean";case "short"->"java.lang.Short";case "float"->"java.lang.Float";case "double"->"java.lang.Double";default->type;};}
    private static boolean identifier(String name){return name!=null&&IDENT.matcher(name).matches();}
    private static String javaString(String raw){return raw.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r");}
    private String packageOf(TypeElement entity){return elements.getPackageOf(entity).getQualifiedName().toString();}
    private Writer file(String pkg,String name,TypeElement entity)throws IOException {return processingEnv.getFiler().createSourceFile(pkg+"."+name,entity).openWriter();}
    private void error(Element el,String message){processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,message,el);}
}
